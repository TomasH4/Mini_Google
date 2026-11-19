package ranking

import (
	"math"
	"reflect"
	"testing"
)

func TestBM25(t *testing.T) {
	want := math.Log(1+8.5/2.5) * 7.5 / 4.5
	got := Score(IDF(10, 2), 3, LengthNorm(100, 100))
	if math.Abs(got-want) > 1e-12 {
		t.Fatalf("score = %.12f; want %.12f", got, want)
	}
	if Score(IDF(4, 1), 0, LengthNorm(0, 0)) != 0 {
		t.Fatal("a term with no occurrences contributed to the score")
	}
}

func TestTopK(t *testing.T) {
	best := NewTopK(3)
	for _, result := range []Result{
		{DocID: 8, Score: 4}, {DocID: 2, Score: 1}, {DocID: 3, Score: 2},
		{DocID: 5, Score: 4}, {DocID: 4, Score: 3}, {DocID: 1, Score: 3},
	} {
		best.Add(result)
	}
	want := []Result{{DocID: 5, Score: 4}, {DocID: 8, Score: 4}, {DocID: 1, Score: 3}}
	if got := best.Results(); !reflect.DeepEqual(got, want) {
		t.Fatalf("TopK = %v; want %v", got, want)
	}
	disabled := NewTopK(0)
	disabled.Add(Result{DocID: 1})
	if len(disabled.Results()) != 0 {
		t.Fatal("zero capacity retained a result")
	}
}

func BenchmarkTopK(b *testing.B) {
	hits := make([]Result, 2000)
	for n := range hits {
		hits[n] = Result{DocID: n + 1, Score: float64(n * 113 % 179)}
	}
	b.ReportAllocs()
	b.ResetTimer()
	for range b.N {
		top := NewTopK(10)
		for _, hit := range hits {
			top.Add(hit)
		}
		if len(top.Results()) != 10 {
			b.Fatal("unexpected result count")
		}
	}
}
