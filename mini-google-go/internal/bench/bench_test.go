package bench

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"minigoogle/internal/index"
)

func TestMeasure(t *testing.T) {
	dir := t.TempDir()
	cfg := Config{CorpusPath: filepath.Join(dir, "corpus.tsv"), QueriesPath: filepath.Join(dir, "queries.txt"), Workers: 4, Warmup: 1}
	for path, content := range map[string]string{
		cfg.CorpusPath:  "1\tLanguage\tFirst\n2\tLanguage\tLast\n",
		cfg.QueriesPath: "First\r\n\nNOT first\r\nunknown\r\n",
	} {
		if err := os.WriteFile(path, []byte(content), 0600); err != nil {
			t.Fatal(err)
		}
	}
	ctx := context.Background()
	run, err := Measure(ctx, cfg)
	if err != nil {
		t.Fatal(err)
	}
	r := run.Report
	if r.Clock == "" || r.TotalDocuments != 2 || r.TotalQueries != 3 || r.ConcurrentWorkers != 3 ||
		r.WarmupQueries != 1 || r.IndexAllocatedBytes == 0 || r.P95LatencyMs > r.P99LatencyMs {
		t.Fatalf("invalid report: %+v", r)
	}
	var encoded bytes.Buffer
	if err := r.WriteJSON(&encoded); err != nil {
		t.Fatal(err)
	}
	var parsed map[string]json.RawMessage
	if err := json.Unmarshal(encoded.Bytes(), &parsed); err != nil {
		t.Fatal(err)
	}
	for _, key := range []string{"clock", "indexTimeMs", "memoryUsedMb", "concurrentThroughputQps", "p95LatencyMs", "p99LatencyMs"} {
		if _, exists := parsed[key]; !exists {
			t.Fatalf("missing %s in JSON", key)
		}
	}
	var dump bytes.Buffer
	if err := run.WriteDump(ctx, &dump); err != nil {
		t.Fatal(err)
	}
	const want = "query\tmatched\ttop10\nFirst\t1\t1:0.693147\nNOT first\t1\t2:0.000000\nunknown\t0\t\n"
	if dump.String() != want {
		t.Fatalf("dump = %q; want %q", dump.String(), want)
	}
}

func TestMeasureConcurrent(t *testing.T) {
	ctx := context.Background()
	timer := monotonicClock()
	idx, err := index.Build(ctx, nil)
	if err != nil {
		t.Fatal(err)
	}
	queries := make([]string, 64)
	for n := range queries {
		queries[n] = "term"
	}
	samples := make([]time.Duration, len(queries))
	for n := range samples {
		samples[n] = -1
	}
	if err := measureConcurrent(ctx, timer, idx, queries, samples, 4); err != nil {
		t.Fatal(err)
	}
	for n, latency := range samples {
		if latency < 0 {
			t.Fatalf("missing sample %d", n)
		}
	}
	queries[2] = "NOT"
	if err := measureConcurrent(ctx, timer, idx, queries, samples, 4); err == nil || !strings.Contains(err.Error(), "expected an operand") {
		t.Fatalf("worker error = %v", err)
	}
	ctx, cancel := context.WithCancel(ctx)
	cancel()
	if err := measureConcurrent(ctx, timer, idx, queries, samples, 4); !errors.Is(err, context.Canceled) {
		t.Fatalf("worker cancellation = %v", err)
	}
}

func TestPercentile(t *testing.T) {
	sorted := make([]time.Duration, 100)
	for n := range sorted {
		sorted[n] = time.Duration(n+1) * time.Millisecond
	}
	if percentile(sorted, .95) != 95 || percentile(sorted, .99) != 99 || percentile(nil, .99) != 0 {
		t.Fatal("expected nearest-rank percentiles")
	}
}

func TestClockElapsed(t *testing.T) {
	timer := clock{now: func() int64 { return 10_014 }, frequency: 10_000_000}
	if got := timer.elapsed(10_000); got != 1400*time.Nanosecond {
		t.Fatalf("elapsed = %s; want 1400ns", got)
	}
}

func BenchmarkConcurrentQueries(b *testing.B) {
	ctx := context.Background()
	timer, err := platformClock()
	if err != nil {
		b.Fatal(err)
	}
	idx, err := index.BuildTSV(ctx, []byte("1\tTitulo\tjava scala\n2\tOtro\tscala concurrente"))
	if err != nil {
		b.Fatal(err)
	}
	queries := make([]string, 100)
	for n := range queries {
		queries[n] = "scala OR concurrente"
	}
	samples := make([]time.Duration, len(queries))
	b.ReportAllocs()
	b.ResetTimer()
	for range b.N {
		if err := measureConcurrent(ctx, timer, idx, queries, samples, 4); err != nil {
			b.Fatal(err)
		}
	}
}
