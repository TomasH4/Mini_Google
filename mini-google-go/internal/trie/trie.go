// Package trie implements vocabulary suggestions and bounded edit-distance search.
package trie

import (
	"cmp"
	"context"
	"fmt"
	"slices"
	"strings"
)

const MaxDistance = 2

type Suggestion struct {
	Term              string
	DocumentFrequency int
	Distance          int
}

type node struct {
	letter      rune
	child, next int
	term        string
	frequency   int
}

// Trie stores child and sibling offsets in a single arena. Its owner protects concurrent access.
type Trie struct{ nodes []node }

func (t *Trie) child(parent int, letter rune) int {
	for child := t.nodes[parent].child; child != 0; child = t.nodes[child].next {
		if t.nodes[child].letter == letter {
			return child
		}
	}
	return 0
}

func (t *Trie) SetFrequency(term string, frequency int) {
	if term == "" || frequency <= 0 {
		return
	}
	if len(t.nodes) == 0 {
		t.nodes = make([]node, 1, 32)
	}
	parent := 0
	for _, letter := range term {
		child := t.child(parent, letter)
		if child == 0 {
			child = len(t.nodes)
			t.nodes = append(t.nodes, node{letter: letter, next: t.nodes[parent].child})
			t.nodes[parent].child = child
		}
		parent = child
	}
	if t.nodes[parent].frequency == 0 {
		t.nodes[parent].term = term
	}
	t.nodes[parent].frequency = frequency
}

func (t *Trie) Suggest(ctx context.Context, prefix string, limit int) ([]Suggestion, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if len(t.nodes) == 0 || limit <= 0 {
		return nil, nil
	}
	parent := 0
	for _, letter := range prefix {
		parent = t.child(parent, letter)
		if parent == 0 {
			return nil, nil
		}
	}

	pending := make([]int, 1, 16)
	pending[0] = parent
	matches := make([]Suggestion, 0, min(limit, 32))
	for len(pending) != 0 {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		current := pending[len(pending)-1]
		pending = pending[:len(pending)-1]
		n := t.nodes[current]
		if n.frequency > 0 {
			matches = append(matches, Suggestion{Term: n.term, DocumentFrequency: n.frequency})
		}
		for child := n.child; child != 0; child = t.nodes[child].next {
			pending = append(pending, child)
		}
	}
	return best(matches, limit), nil
}

// Fuzzy searches by rune edit distance, then document frequency, then spelling.
func (t *Trie) Fuzzy(ctx context.Context, term string, distance, limit int) ([]Suggestion, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if distance < 0 || distance > MaxDistance {
		return nil, fmt.Errorf("edit distance must be between 0 and %d", MaxDistance)
	}
	if len(t.nodes) == 0 || limit <= 0 {
		return nil, nil
	}
	word := []rune(term)
	initial := editRow{width: min(len(word), distance) + 1}
	for column := range initial.width {
		initial.cost[column] = column
	}
	matches := make([]Suggestion, 0, min(limit, 32))
	if err := t.fuzzy(ctx, 0, word, 1, distance, initial, &matches); err != nil {
		return nil, err
	}
	return best(matches, limit), nil
}

// A row stores only columns within the maximum edit distance of the prefix length.
type editRow struct {
	start, width int
	cost         [2*MaxDistance + 1]int
}

func (r editRow) at(column int) int {
	offset := column - r.start
	if offset < 0 || offset >= r.width {
		return MaxDistance + 1
	}
	return r.cost[offset]
}

func (t *Trie) fuzzy(ctx context.Context, parent int, word []rune, depth, distance int, previous editRow, matches *[]Suggestion) error {
	for child := t.nodes[parent].child; child != 0; child = t.nodes[child].next {
		if err := ctx.Err(); err != nil {
			return err
		}
		n := t.nodes[child]
		start := max(0, depth-distance)
		row := editRow{start: start, width: min(len(word), depth+distance) - start + 1}
		lowest := MaxDistance + 1
		for offset := range row.width {
			column := row.start + offset
			cost := depth
			if column > 0 {
				substitution := previous.at(column - 1)
				if n.letter != word[column-1] {
					substitution++
				}
				cost = min(previous.at(column)+1, row.at(column-1)+1, substitution)
			}
			row.cost[offset] = cost
			lowest = min(lowest, cost)
		}
		if lowest > distance {
			continue
		}
		if edits := row.at(len(word)); n.frequency > 0 && edits <= distance {
			*matches = append(*matches, Suggestion{Term: n.term, DocumentFrequency: n.frequency, Distance: edits})
		}
		if err := t.fuzzy(ctx, child, word, depth+1, distance, row, matches); err != nil {
			return err
		}
	}
	return nil
}

func best(matches []Suggestion, limit int) []Suggestion {
	slices.SortFunc(matches, func(a, b Suggestion) int {
		if order := cmp.Compare(a.Distance, b.Distance); order != 0 {
			return order
		}
		if order := cmp.Compare(b.DocumentFrequency, a.DocumentFrequency); order != 0 {
			return order
		}
		return strings.Compare(a.Term, b.Term)
	})
	return matches[:min(len(matches), limit)]
}
