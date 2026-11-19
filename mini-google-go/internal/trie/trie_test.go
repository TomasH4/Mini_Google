package trie

import (
	"context"
	"errors"
	"reflect"
	"testing"
)

func TestSuggest(t *testing.T) {
	var words Trie
	words.SetFrequency("casa", 1)
	words.SetFrequency("caso", 4)
	words.SetFrequency("casar", 4)
	words.SetFrequency("casa", 5)
	got, err := words.Suggest(context.Background(), "cas", 2)
	want := []Suggestion{{Term: "casa", DocumentFrequency: 5}, {Term: "casar", DocumentFrequency: 4}}
	if err != nil || !reflect.DeepEqual(got, want) {
		t.Fatalf("Suggest = %v, %v; want %v", got, err, want)
	}
}

func TestFuzzyAgainstEditDistance(t *testing.T) {
	ctx := context.Background()
	terms := []string{"a", "á", "mañana", "mano", "mana", "caña", "mañanita", "casa", "caso", "cazar", "🐈", "gato🐈"}
	var words Trie
	for _, text := range terms {
		words.SetFrequency(text, 1)
	}
	for _, text := range append(terms, "", "mañna", "mañanna", "mazana", "man", "nada") {
		for maxEdits := 0; maxEdits <= MaxDistance; maxEdits++ {
			got, err := words.Fuzzy(ctx, text, maxEdits, len(terms))
			if err != nil {
				t.Fatal(err)
			}
			want := make(map[string]int, len(terms))
			for _, term := range terms {
				if edits := editDistance([]rune(text), []rune(term)); edits <= maxEdits {
					want[term] = edits
				}
			}
			actual := make(map[string]int, len(got))
			for _, result := range got {
				actual[result.Term] = result.Distance
			}
			if !reflect.DeepEqual(actual, want) {
				t.Fatalf("Fuzzy(%q, %d) = %v; want %v", text, maxEdits, actual, want)
			}
		}
	}
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := words.Fuzzy(canceled, "ca", 2, 10); !errors.Is(err, context.Canceled) {
		t.Fatalf("canceled fuzzy returned %v", err)
	}
}

func editDistance(a, b []rune) int {
	cost := make([][]int, len(a)+1)
	for i := range cost {
		cost[i] = make([]int, len(b)+1)
		cost[i][0] = i
	}
	for j := range b {
		cost[0][j+1] = j + 1
	}
	for i, left := range a {
		for j, right := range b {
			substitution := cost[i][j]
			if left != right {
				substitution++
			}
			cost[i+1][j+1] = min(cost[i][j+1]+1, cost[i+1][j]+1, substitution)
		}
	}
	return cost[len(a)][len(b)]
}

func BenchmarkFuzzy(b *testing.B) {
	ctx := context.Background()
	var words Trie
	for _, term := range []string{"concurrencia", "concurrente", "concurrencias", "concurrir", "concertar", "cuerdas", "correr"} {
		words.SetFrequency(term, 3)
	}
	b.ReportAllocs()
	b.ResetTimer()
	for range b.N {
		if _, err := words.Fuzzy(ctx, "concurrensia", 2, 3); err != nil {
			b.Fatal(err)
		}
	}
}
