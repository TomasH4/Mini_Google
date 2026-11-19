package bench

import (
	"context"
	"fmt"
	"io"
	"os"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	"minigoogle/internal/index"
	"minigoogle/internal/trie"
)

func lookup(ctx context.Context, idx *index.Index, text string) (index.Matches, error) {
	if strings.HasPrefix(text, "~") || strings.HasSuffix(text, "*") {
		var suggestions []trie.Suggestion
		var err error
		if word, ok := strings.CutPrefix(text, "~"); ok {
			suggestions, err = idx.Fuzzy(ctx, word, 2, 1)
		} else {
			suggestions, err = idx.Suggest(ctx, strings.TrimSuffix(text, "*"), 1)
		}
		if err != nil || len(suggestions) == 0 {
			return index.Matches{}, err
		}
		text = suggestions[0].Term
	}
	return idx.Search(ctx, text, index.SearchOptions{})
}

func warmSuite(ctx context.Context, timer clock, corpus []byte, qs []string, writes []index.Document, out io.Writer) error {
	idx, err := index.BuildTSV(ctx, corpus)
	if err != nil {
		return err
	}
	if _, err = lookup(ctx, idx, qs[0]); err != nil {
		return err
	}
	fmt.Fprintln(out, "READY")
	for n := 0; n < 2000; n++ {
		if _, err := lookup(ctx, idx, qs[n%len(qs)]); err != nil {
			return err
		}
	}
	_, err = mixedSuite(ctx, timer, idx, qs, writes[:200])
	return err
}

func mixedSuite(ctx context.Context, timer clock, idx *index.Index, qs []string, writes []index.Document) (float64, error) {
	var done, ready sync.WaitGroup
	start := make(chan struct{})
	errs := make(chan error, 5)
	reads := 4 * len(writes)
	initial := idx.Stats().Documents
	for lane := range 5 {
		done.Add(1)
		ready.Add(1)
		go func() {
			defer done.Done()
			ready.Done()
			<-start
			if lane == 4 {
				for _, doc := range writes {
					if err := idx.Add(ctx, doc); err != nil {
						errs <- err
						return
					}
				}
			} else {
				for n := lane; n < reads; n += 4 {
					if _, err := lookup(ctx, idx, qs[n]); err != nil {
						errs <- err
						return
					}
				}
			}
		}()
	}
	ready.Wait()
	tick := timer.now()
	close(start)
	done.Wait()
	seconds := timer.elapsed(tick).Seconds()
	close(errs)
	for err := range errs {
		return 0, err
	}
	if idx.Stats().Documents != initial+len(writes) {
		return 0, fmt.Errorf("missing writes")
	}
	return float64(reads) / seconds, nil
}

// RunSuite implements the cross-language benchmark process protocol.
func RunSuite(ctx context.Context, mode, corpusPath, queryPath, writesPath string, in io.Reader, out io.Writer) error {
	raw, err := os.ReadFile(corpusPath)
	if err != nil {
		return err
	}
	data, err := os.ReadFile(queryPath)
	if err != nil {
		return err
	}
	queries := splitQueries(string(data))
	if len(queries) == 0 {
		return fmt.Errorf("empty workload")
	}
	if mode == "parity" {
		idx, err := index.BuildTSV(ctx, raw)
		if err != nil {
			return err
		}
		for _, q := range queries {
			result, err := lookup(ctx, idx, q)
			if err != nil {
				return err
			}
			hits := make([]string, 0, len(result.Top))
			for _, hit := range result.Top {
				hits = append(hits, fmt.Sprintf("%d:%.15g", hit.DocID, hit.Score))
			}
			fmt.Fprintln(out, "HITS "+strings.Join(hits, ","))
		}
		return nil
	}
	if mode != "measure" && mode != "index" {
		return fmt.Errorf("unknown mode %q", mode)
	}
	data, err = os.ReadFile(writesPath)
	if err != nil {
		return err
	}
	lines := splitQueries(string(data))
	writes := make([]index.Document, 0, len(lines))
	for _, line := range lines {
		cols := strings.SplitN(line, "\t", 3)
		if len(cols) != 3 {
			return fmt.Errorf("invalid write row")
		}
		id, err := strconv.Atoi(cols[0])
		if err != nil {
			return err
		}
		writes = append(writes, index.Document{ID: id, Title: cols[1], Body: cols[2]})
	}
	if len(writes)*5 != len(queries) {
		return fmt.Errorf("unexpected workload size")
	}
	timer, err := platformClock()
	if err != nil {
		return err
	}
	if err := warmSuite(ctx, timer, raw, queries, writes, out); err != nil {
		return err
	}
	before := memory()
	start := timer.now()
	idx, err := index.BuildTSV(ctx, raw)
	if err != nil {
		return err
	}
	indexMs := ms(timer.elapsed(start))
	after := memory()
	heap := (float64(after.HeapAlloc) - float64(before.HeapAlloc)) / 1048576
	if mode == "index" {
		fmt.Fprintf(out, "INDEX %.9f %.9f\n", indexMs, heap)
		runtime.KeepAlive(raw)
		runtime.KeepAlive(idx)
		var ack [1]byte
		_, err = in.Read(ack[:])
		return err
	}
	durations := make([]time.Duration, len(queries))
	start = timer.now()
	for n, text := range queries {
		tick := timer.now()
		if _, err := lookup(ctx, idx, text); err != nil {
			return err
		}
		durations[n] = timer.elapsed(tick)
	}
	single := qps(len(queries), timer.elapsed(start))
	concurrent, err := mixedSuite(ctx, timer, idx, queries, writes)
	if err != nil {
		return err
	}
	slices.Sort(durations)
	fmt.Fprintf(out, "METRICS %.9f %.9f %.9f %.9f", indexMs, heap, single, concurrent)
	for _, p := range []float64{.5, .9, .95, .99, .999} {
		fmt.Fprintf(out, " %.9f", percentile(durations, p)*1000)
	}
	fmt.Fprintln(out)
	runtime.KeepAlive(raw)
	var ack [1]byte
	_, err = in.Read(ack[:])
	return err
}
