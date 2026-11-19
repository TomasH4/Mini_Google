// Package ranking implements BM25 and bounded result selection.
package ranking

import (
	"container/heap"
	"math"
	"slices"
)

const k1, b = 1.5, 0.75

type Result struct {
	DocID int
	Title string
	Score float64
}

func IDF(documents, frequency int) float64 {
	return math.Log(1 + (float64(documents-frequency)+0.5)/(float64(frequency)+0.5))
}

func LengthNorm(length int, average float64) float64 {
	if average == 0 {
		return k1
	}
	return k1 * (1 - b + b*float64(length)/average)
}

func Score(weight float64, frequency int, norm float64) float64 {
	f := float64(frequency)
	return weight * (f * (k1 + 1)) / (f + norm)
}

// TopK keeps the worst retained result at the root. Ties favor lower IDs.
type TopK struct {
	limit int
	items resultHeap
}

func NewTopK(k int) TopK {
	k = max(k, 0)
	return TopK{limit: k, items: make(resultHeap, 0, k)}
}

func (t *TopK) Add(result Result) {
	switch {
	case t.limit == 0:
	case len(t.items) < t.limit:
		// Initialize in one pass when the heap first reaches capacity.
		t.items = append(t.items, result)
		if len(t.items) == t.limit {
			heap.Init(&t.items)
		}
	case compare(result, t.items[0]) < 0:
		t.items[0] = result
		heap.Fix(&t.items, 0)
	}
}

// Results returns the retained hits in rank order and consumes the heap.
func (t *TopK) Results() []Result {
	results := t.items
	t.items = nil
	t.limit = 0
	slices.SortFunc(results, compare)
	return results
}

func compare(a, b Result) int {
	switch {
	case a.Score > b.Score:
		return -1
	case a.Score < b.Score:
		return 1
	case a.DocID < b.DocID:
		return -1
	case a.DocID > b.DocID:
		return 1
	default:
		return 0
	}
}

type resultHeap []Result

func (h resultHeap) Len() int           { return len(h) }
func (h resultHeap) Less(i, j int) bool { return compare(h[i], h[j]) > 0 }
func (h resultHeap) Swap(i, j int)      { h[i], h[j] = h[j], h[i] }
func (h *resultHeap) Push(x any)        { *h = append(*h, x.(Result)) }
func (h *resultHeap) Pop() any {
	last := len(*h) - 1
	x := (*h)[last]
	(*h)[last] = Result{}
	*h = (*h)[:last]
	return x
}
