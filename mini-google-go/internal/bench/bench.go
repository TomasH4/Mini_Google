// Package bench measures disk loading, in-memory indexing and query execution.
package bench

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"os"
	"runtime"
	"slices"
	"strings"
	"sync"
	"time"

	"minigoogle/internal/index"
)

type Config struct {
	CorpusPath  string
	QueriesPath string
	Warmup      int
	Workers     int
}

type Report struct {
	Language                string  `json:"language"`
	Runtime                 string  `json:"runtime"`
	Clock                   string  `json:"clock"`
	GOMAXPROCS              int     `json:"gomaxprocs"`
	TotalDocuments          int     `json:"totalDocuments"`
	Vocabulary              int     `json:"vocabulary"`
	LoadTimeMs              float64 `json:"loadTimeMs"`
	IndexTimeMs             float64 `json:"indexTimeMs"`
	MemoryUsedMb            float64 `json:"memoryUsedMb"`
	IndexAllocatedBytes     uint64  `json:"indexAllocatedBytes"`
	TotalQueries            int     `json:"totalQueries"`
	WarmupQueries           int     `json:"warmupQueries"`
	SequentialTimeMs        float64 `json:"sequentialTimeMs"`
	SequentialThroughputQPS float64 `json:"sequentialThroughputQps"`
	MeanLatencyMs           float64 `json:"meanLatencyMs"`
	P50LatencyMs            float64 `json:"p50LatencyMs"`
	P95LatencyMs            float64 `json:"p95LatencyMs"`
	P99LatencyMs            float64 `json:"p99LatencyMs"`
	ConcurrentTimeMs        float64 `json:"concurrentTimeMs"`
	ConcurrentThroughputQPS float64 `json:"concurrentThroughputQps"`
	ConcurrentP95LatencyMs  float64 `json:"concurrentP95LatencyMs"`
	ConcurrentP99LatencyMs  float64 `json:"concurrentP99LatencyMs"`
	ConcurrentWorkers       int     `json:"concurrentWorkers"`
}

type Run struct {
	Report  Report
	index   *index.Index
	queries []string
}

func Measure(ctx context.Context, cfg Config) (*Run, error) {
	if cfg.Warmup < 0 || cfg.Workers <= 0 {
		return nil, fmt.Errorf("warmup must be nonnegative and workers must be positive")
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	timer, err := platformClock()
	if err != nil {
		return nil, err
	}
	start := timer.now()
	corpus, err := os.ReadFile(cfg.CorpusPath)
	loadTime := timer.elapsed(start)
	if err != nil {
		return nil, fmt.Errorf("read corpus: %w", err)
	}
	rawQueries, err := os.ReadFile(cfg.QueriesPath)
	if err != nil {
		return nil, fmt.Errorf("read queries: %w", err)
	}
	queries := splitQueries(string(rawQueries))
	if len(queries) == 0 {
		return nil, fmt.Errorf("query file contains no queries")
	}

	before := memory()
	start = timer.now()
	idx, err := index.BuildTSV(ctx, corpus)
	indexTime := timer.elapsed(start)
	if err != nil {
		return nil, fmt.Errorf("build index: %w", err)
	}
	after := memory()
	// The preloaded bytes must be live in both samples.
	runtime.KeepAlive(corpus)
	var retained uint64
	if after.HeapAlloc > before.HeapAlloc {
		retained = after.HeapAlloc - before.HeapAlloc
	}

	warmup := min(cfg.Warmup, len(queries))
	for _, text := range queries[:warmup] {
		if _, err := idx.Search(ctx, text, index.SearchOptions{}); err != nil {
			return nil, fmt.Errorf("warmup query %q: %w", text, err)
		}
	}
	latencies := make([]time.Duration, len(queries))
	start = timer.now()
	for n, text := range queries {
		if err := measureQuery(ctx, timer, idx, text, &latencies[n]); err != nil {
			return nil, err
		}
	}
	sequential := timer.elapsed(start)

	workers := min(cfg.Workers, len(queries))
	concurrentLatencies := make([]time.Duration, len(queries))
	start = timer.now()
	if err := measureConcurrent(ctx, timer, idx, queries, concurrentLatencies, workers); err != nil {
		return nil, err
	}
	concurrent := timer.elapsed(start)

	slices.Sort(latencies)
	slices.Sort(concurrentLatencies)
	var total time.Duration
	for _, latency := range latencies {
		total += latency
	}
	stats := idx.Stats()
	report := Report{
		Language: "Go", Runtime: runtime.Version(), Clock: timer.source, GOMAXPROCS: runtime.GOMAXPROCS(0),
		TotalDocuments: stats.Documents, Vocabulary: stats.Vocabulary,
		LoadTimeMs: ms(loadTime), IndexTimeMs: ms(indexTime),
		MemoryUsedMb:        float64(retained) / (1024 * 1024),
		IndexAllocatedBytes: after.TotalAlloc - before.TotalAlloc,
		TotalQueries:        len(queries), WarmupQueries: warmup,
		SequentialTimeMs: ms(sequential), SequentialThroughputQPS: qps(len(queries), sequential),
		MeanLatencyMs:    ms(total) / float64(len(latencies)),
		P50LatencyMs:     percentile(latencies, 0.50),
		P95LatencyMs:     percentile(latencies, 0.95),
		P99LatencyMs:     percentile(latencies, 0.99),
		ConcurrentTimeMs: ms(concurrent), ConcurrentThroughputQPS: qps(len(queries), concurrent),
		ConcurrentP95LatencyMs: percentile(concurrentLatencies, 0.95),
		ConcurrentP99LatencyMs: percentile(concurrentLatencies, 0.99),
		ConcurrentWorkers:      workers,
	}
	return &Run{Report: report, index: idx, queries: queries}, nil
}

func splitQueries(raw string) []string {
	lines := strings.Split(raw, "\n")
	queries := lines[:0]
	for _, line := range lines {
		if text := strings.TrimSpace(line); text != "" {
			queries = append(queries, text)
		}
	}
	clear(lines[len(queries):])
	return queries
}

func measureQuery(ctx context.Context, timer clock, idx *index.Index, text string, latency *time.Duration) error {
	start := timer.now()
	if _, err := idx.Search(ctx, text, index.SearchOptions{}); err != nil {
		return fmt.Errorf("query %q: %w", text, err)
	}
	*latency = timer.elapsed(start)
	return nil
}

func measureConcurrent(ctx context.Context, timer clock, idx *index.Index, queries []string, latencies []time.Duration, workers int) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	jobs := make(chan int, workers)
	var wg sync.WaitGroup
	var first sync.Once
	var queryErr error
	for range workers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for n := range jobs {
				if err := ctx.Err(); err != nil {
					return
				}
				if err := measureQuery(ctx, timer, idx, queries[n], &latencies[n]); err != nil {
					first.Do(func() {
						queryErr = err
						cancel()
					})
					return
				}
			}
		}()
	}
send:
	for n := range queries {
		select {
		case <-ctx.Done():
			break send
		case jobs <- n:
		}
	}
	close(jobs)
	wg.Wait()
	if queryErr != nil {
		return queryErr
	}
	return ctx.Err()
}

func memory() runtime.MemStats {
	runtime.GC()
	var stats runtime.MemStats
	runtime.ReadMemStats(&stats)
	return stats
}

func ms(d time.Duration) float64 { return float64(d) / float64(time.Millisecond) }

func qps(count int, duration time.Duration) float64 {
	if duration <= 0 {
		return 0
	}
	return float64(count) / duration.Seconds()
}

func percentile(sorted []time.Duration, p float64) float64 {
	if len(sorted) == 0 {
		return 0
	}
	return ms(sorted[int(math.Ceil(float64(len(sorted))*p))-1])
}

func (r Report) WriteJSON(w io.Writer) error {
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	return encoder.Encode(r)
}

// WriteDump emits the same query, matched count and top-10 columns as Scala.
// It performs a fresh pass outside the benchmark measurements.
func (r *Run) WriteDump(ctx context.Context, w io.Writer) error {
	out := bufio.NewWriter(w)
	if _, err := fmt.Fprintln(out, "query\tmatched\ttop10"); err != nil {
		return err
	}
	for _, text := range r.queries {
		matches, err := r.index.Search(ctx, text, index.SearchOptions{})
		if err != nil {
			return fmt.Errorf("dump query %q: %w", text, err)
		}
		var line strings.Builder
		line.Grow(len(text) + 16 + len(matches.Top)*20)
		// Control characters are escaped to keep every query on one TSV row.
		fmt.Fprintf(&line, "%s\t%d\t", strings.ReplaceAll(text, "\t", "\\t"), matches.Total)
		for n, result := range matches.Top {
			if n != 0 {
				line.WriteByte(';')
			}
			fmt.Fprintf(&line, "%d:%.6f", result.DocID, result.Score)
		}
		line.WriteByte('\n')
		if _, err := out.WriteString(line.String()); err != nil {
			return err
		}
	}
	return out.Flush()
}
