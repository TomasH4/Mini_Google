package index

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"sync"
	"testing"

	"minigoogle/internal/query"
)

const fixture = "4\tCuarto\tjava !!! go\r\n" +
	"2\tSegundo\tJAVA go go\r\n" +
	"1\tPrimero\tgo scala\r\n" +
	"3\tTercero\tÁrbol índice-invertido concurrencia\r\n" +
	"5\tQuinto\t"

func TestSearch(t *testing.T) {
	ctx := context.Background()
	idx, err := BuildTSV(ctx, []byte(fixture))
	if err != nil {
		t.Fatal(err)
	}
	tests := []struct {
		text string
		want []int
	}{
		{`("java go" OR (scala AND NOT java))`, []int{1, 2}},
		{`java AND NOT "java go"`, []int{4}},
		{`"go go"`, []int{2}},
		{`"go java"`, nil},
		{`"!!!"`, nil},
		{"álgebra", nil},
		{"árbol índice-invertido", []int{3}},
		{"NOT (java OR scala)", []int{3, 5}},
		{"NOT NOT scala", []int{1}},
	}
	for _, tc := range tests {
		t.Run(tc.text, func(t *testing.T) {
			got, err := idx.Search(ctx, tc.text, SearchOptions{})
			if err != nil {
				t.Fatal(err)
			}
			ids := make([]int, 0, len(got.Top))
			for _, hit := range got.Top {
				ids = append(ids, hit.DocID)
			}
			if !slices.Equal(ids, tc.want) || got.Total != len(tc.want) {
				t.Fatalf("Search = %v (%d matches); want %v", ids, got.Total, tc.want)
			}
		})
	}
	positive, err := idx.Search(ctx, "scala", SearchOptions{})
	if err != nil {
		t.Fatal(err)
	}
	repeated, err := idx.Search(ctx, "scala OR scala", SearchOptions{})
	if err != nil {
		t.Fatal(err)
	}
	withNegation, err := idx.Search(ctx, "scala OR NOT java", SearchOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if positive.Top[0].Score*2 != repeated.Top[0].Score || withNegation.Top[0] != positive.Top[0] {
		t.Fatalf("unexpected scores: %v, %v, %v", positive.Top, repeated.Top, withNegation.Top)
	}
	if _, err := idx.Search(ctx, "", SearchOptions{}); !errors.Is(err, query.ErrEmpty) {
		t.Fatalf("Search empty = %v", err)
	}
	eval := evaluator{index: idx}
	if _, err := eval.match(ctx, nil, true); err == nil {
		t.Fatal("unexpected AST node was accepted")
	}
}

func TestFuzzyOptIn(t *testing.T) {
	ctx := context.Background()
	idx, err := BuildTSV(ctx, []byte(fixture))
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		text     string
		distance int
		want     int
	}{
		{"concurrensia", 0, 0},
		{"concurrensia", 1, 1},
		{`"concurrensia"`, 1, 0},
		{"NOT concurrensia", 1, 5},
	} {
		got, err := idx.Search(ctx, tc.text, SearchOptions{FuzzyDistance: tc.distance})
		if err != nil || got.Total != tc.want {
			t.Fatalf("Search(%q, %d) = %v, %v; want %d matches", tc.text, tc.distance, got, err, tc.want)
		}
	}
}

func TestBuildAndAdd(t *testing.T) {
	ctx := context.Background()
	for _, data := range []string{"id\tTítulo\tcuerpo", "1\tsin cuerpo", "1\tx\tcuerpo\textra", "1\tx\t\xff"} {
		if _, err := BuildTSV(ctx, []byte(data)); err == nil {
			t.Fatalf("accepted invalid TSV %q", data)
		}
	}
	if _, err := BuildTSV(ctx, []byte("1\tx\tcuerpo\n1\ty\totro")); !errors.Is(err, ErrDuplicateID) {
		t.Fatalf("duplicate row: %v", err)
	}
	idx, err := BuildTSV(ctx, []byte("3\ttítulo\t"+strings.Repeat("go ", 40_000)+"cola"))
	if err != nil {
		t.Fatal(err)
	}
	if err := idx.Add(ctx, Document{ID: 1, Title: "nueva", Body: "go cola"}); err != nil {
		t.Fatal(err)
	}
	if err := idx.Add(ctx, Document{ID: 1, Body: "invisible"}); !errors.Is(err, ErrDuplicateID) {
		t.Fatalf("duplicate add: %v", err)
	}
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	if err := idx.Add(canceled, Document{ID: 2, Body: "invisible"}); !errors.Is(err, context.Canceled) {
		t.Fatalf("canceled add: %v", err)
	}
	if _, err := idx.Search(canceled, "go", SearchOptions{}); !errors.Is(err, context.Canceled) {
		t.Fatalf("canceled query: %v", err)
	}
	got, err := idx.Search(ctx, `"go cola" AND NOT invisible`, SearchOptions{})
	if err != nil || got.Total != 2 {
		t.Fatalf("postings after out-of-order insert = %v, %v", got, err)
	}
	suggestions, err := idx.Suggest(ctx, "g", 1)
	if err != nil || len(suggestions) != 1 || suggestions[0].Term != "go" || suggestions[0].DocumentFrequency != 2 {
		t.Fatalf("suggestions after add = %v, %v", suggestions, err)
	}
}

func TestConcurrentReadersAndWriters(t *testing.T) {
	ctx := context.Background()
	var idx Index
	const readers, writers, perWriter = 6, 4, 32
	start := make(chan struct{})
	var wg sync.WaitGroup
	for writer := range writers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for n := range perWriter {
				// Descending IDs also exercise insertion into existing postings.
				id := writer*perWriter + perWriter - n
				if err := idx.Add(ctx, Document{ID: id, Title: "visible", Body: "shared visible"}); err != nil {
					t.Errorf("add %d: %v", id, err)
					return
				}
			}
		}()
	}
	for range readers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for range 64 {
				got, err := idx.Search(ctx, "shared AND NOT visible", SearchOptions{})
				if err != nil || got.Total != 0 {
					t.Errorf("found inconsistent posting lists: %v, %v", got, err)
					return
				}
				if _, err := idx.Fuzzy(ctx, "visibl", 1, 2); err != nil {
					t.Error(err)
					return
				}
			}
		}()
	}
	close(start)
	wg.Wait()
	results, err := idx.Search(ctx, `"shared visible"`, SearchOptions{})
	if err != nil || results.Total != writers*perWriter || len(results.Top) != topK {
		t.Fatalf("final results = %v, %v", results, err)
	}
	for at, result := range results.Top {
		if result.DocID != at+1 || result.Title != "visible" {
			t.Fatalf("unexpected tie ordering or title: %v", results.Top)
		}
	}
}

func benchmarkCorpus() []byte {
	var corpus bytes.Buffer
	corpus.Grow(100_000)
	for id := 1; id <= 1000; id++ {
		fmt.Fprintf(&corpus, "%d\tTítulo %d\tUn índice posicional para java y scala con hilos virtuales", id, id)
		if id%7 == 0 {
			corpus.WriteString(" y concurrencia")
		}
		corpus.WriteByte('\n')
	}
	return corpus.Bytes()
}

func BenchmarkBuildTSV(b *testing.B) {
	ctx, data := context.Background(), benchmarkCorpus()
	b.SetBytes(int64(len(data)))
	b.ReportAllocs()
	b.ResetTimer()
	for range b.N {
		if _, err := BuildTSV(ctx, data); err != nil {
			b.Fatal(err)
		}
	}
}

func BenchmarkSearch(b *testing.B) {
	ctx := context.Background()
	idx, err := BuildTSV(ctx, benchmarkCorpus())
	if err != nil {
		b.Fatal(err)
	}
	queries := []string{"concurrencia", `"hilos virtuales"`, "(java OR scala) AND NOT concurrencia"}
	b.ReportAllocs()
	b.ResetTimer()
	for n := range b.N {
		if _, err := idx.Search(ctx, queries[n%len(queries)], SearchOptions{}); err != nil {
			b.Fatal(err)
		}
	}
}
