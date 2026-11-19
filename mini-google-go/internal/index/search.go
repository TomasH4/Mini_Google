package index

import (
	"context"
	"fmt"
	"slices"
	"strings"

	"minigoogle/internal/query"
	"minigoogle/internal/ranking"
	"minigoogle/internal/trie"
)

type SearchOptions struct {
	// FuzzyDistance enables correction of absent, unquoted terms when positive.
	FuzzyDistance int
}

type Matches struct {
	Total int
	Top   []ranking.Result
}

const topK = 10

// Search parses, evaluates, and ranks against one consistent view of the index.
func (i *Index) Search(ctx context.Context, text string, opts SearchOptions) (Matches, error) {
	if err := ctx.Err(); err != nil {
		return Matches{}, err
	}
	if opts.FuzzyDistance < 0 || opts.FuzzyDistance > trie.MaxDistance {
		return Matches{}, fmt.Errorf("fuzzy distance must be between 0 and %d", trie.MaxDistance)
	}
	node, err := query.Parse(text)
	if err != nil {
		return Matches{}, fmt.Errorf("parse query: %w", err)
	}
	i.mu.RLock()
	defer i.mu.RUnlock()
	eval := evaluator{index: i, terms: make(map[string]int, 4), fuzzyDistance: opts.FuzzyDistance}
	ids, err := eval.match(ctx, node, true)
	if err != nil {
		return Matches{}, err
	}
	hits, err := i.rank(ctx, ids, eval.terms)
	if err != nil {
		return Matches{}, err
	}
	return Matches{Total: len(ids), Top: hits}, nil
}

type evaluator struct {
	index         *Index
	terms         map[string]int
	fuzzyDistance int
}

func (e *evaluator) match(ctx context.Context, node query.Node, positive bool) ([]int, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	switch n := node.(type) {
	case query.Term:
		word := normalize(n.Text)
		postings := e.index.terms[word]
		if postings == nil && word != "" && positive && e.fuzzyDistance > 0 {
			matches, err := e.index.vocabulary.Fuzzy(ctx, word, e.fuzzyDistance, 1)
			if err != nil {
				return nil, fmt.Errorf("correct term %q: %w", word, err)
			}
			if len(matches) > 0 {
				word = matches[0].Term
				postings = e.index.terms[word]
			}
		}
		if postings == nil {
			return nil, nil
		}
		if positive {
			e.terms[word]++
		}
		return postings.ids, nil
	case query.Phrase:
		lists := make([]*postingList, 0, strings.Count(n.Text, " ")+1)
		scanWords(n.Text, func(word string, _ int) {
			postings := e.index.terms[word]
			lists = append(lists, postings)
			if positive && postings != nil {
				e.terms[word]++
			}
		})
		return phrase(ctx, lists)
	case query.And:
		left, err := e.match(ctx, n.Left, positive)
		if err != nil {
			return nil, err
		}
		right, err := e.match(ctx, n.Right, positive)
		if err != nil {
			return nil, err
		}
		return intersect(left, right), nil
	case query.Or:
		left, err := e.match(ctx, n.Left, positive)
		if err != nil {
			return nil, err
		}
		right, err := e.match(ctx, n.Right, positive)
		if err != nil {
			return nil, err
		}
		return union(left, right), nil
	case query.Not:
		excluded, err := e.match(ctx, n.Child, !positive)
		if err != nil {
			return nil, err
		}
		return difference(e.index.ids, excluded), nil
	default:
		return nil, fmt.Errorf("unsupported query node %T", node)
	}
}

func intersect(left, right []int) []int {
	if len(left) == 0 || len(right) == 0 {
		return nil
	}
	out := make([]int, 0, min(len(left), len(right)))
	for a, b := 0, 0; a < len(left) && b < len(right); {
		switch {
		case left[a] < right[b]:
			a++
		case left[a] > right[b]:
			b++
		default:
			out = append(out, left[a])
			a++
			b++
		}
	}
	return out
}

func union(left, right []int) []int {
	if len(left) == 0 {
		return right
	}
	if len(right) == 0 {
		return left
	}
	out := make([]int, 0, len(left)+len(right))
	a, b := 0, 0
	for a < len(left) && b < len(right) {
		if left[a] < right[b] {
			out = append(out, left[a])
			a++
			continue
		}
		if left[a] == right[b] {
			a++
		}
		out = append(out, right[b])
		b++
	}
	out = append(out, left[a:]...)
	return append(out, right[b:]...)
}

func difference(all, excluded []int) []int {
	if len(all) == 0 || len(excluded) == 0 {
		return all
	}
	out := make([]int, 0, len(all)-len(excluded))
	ex := 0
	for _, id := range all {
		if ex < len(excluded) && id == excluded[ex] {
			ex++
		} else {
			out = append(out, id)
		}
	}
	return out
}

func phrase(ctx context.Context, lists []*postingList) ([]int, error) {
	if len(lists) == 0 {
		return nil, nil
	}
	anchor := lists[0]
	for _, list := range lists {
		if list == nil {
			return nil, nil
		}
		if len(list.ids) < len(anchor.ids) {
			anchor = list
		}
	}
	if len(lists) == 1 {
		return anchor.ids, nil
	}
	cursors := make([]postingCursor, len(lists))
	for word, list := range lists {
		cursors[word] = newPostingCursor(list, len(anchor.ids))
	}
	positions := make([][]int, len(lists))
	out := make([]int, 0, len(anchor.ids))
candidate:
	for at, id := range anchor.ids {
		if at&1023 == 0 {
			if err := ctx.Err(); err != nil {
				return nil, err
			}
		}
		for word, list := range lists {
			if list == anchor {
				positions[word] = list.positionsAt(at)
				continue
			}
			foundAt, found := cursors[word].seek(id)
			if foundAt == len(list.ids) {
				return out, nil
			}
			if !found {
				continue candidate
			}
			positions[word] = list.positionsAt(foundAt)
		}
		if adjacent(positions) {
			out = append(out, id)
		}
	}
	return out, nil
}

func adjacent(positions [][]int) bool {
startPosition:
	for _, start := range positions[0] {
		for offset, wordPositions := range positions[1:] {
			if _, found := slices.BinarySearch(wordPositions, start+offset+1); !found {
				continue startPosition
			}
		}
		return true
	}
	return false
}

type postingCursor struct {
	list   *postingList
	at     int
	sparse bool
}

func newPostingCursor(list *postingList, candidates int) postingCursor {
	return postingCursor{list: list, sparse: len(list.ids)/16 > candidates}
}

// seek visits increasing IDs. A sparse workload skips long stretches by binary search.
func (c *postingCursor) seek(id int) (int, bool) {
	ids := c.list.ids
	if c.sparse {
		return slices.BinarySearch(ids, id)
	}
	for c.at < len(ids) && ids[c.at] < id {
		c.at++
	}
	return c.at, c.at < len(ids) && ids[c.at] == id
}

type weightedTerm struct {
	text   string
	cursor postingCursor
	weight float64
}

func (i *Index) rank(ctx context.Context, ids []int, terms map[string]int) ([]ranking.Result, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	k := min(topK, len(ids))
	if k == 0 || len(terms) == 0 {
		top := make([]ranking.Result, 0, k)
		for _, id := range ids[:k] {
			top = append(top, ranking.Result{DocID: id, Title: i.docs[id].title})
		}
		return top, nil
	}
	weighted := make([]weightedTerm, 0, len(terms))
	average := float64(i.totalLength) / float64(len(i.ids))
	for term, repetitions := range terms {
		postings := i.terms[term]
		weighted = append(weighted, weightedTerm{
			text:   term,
			cursor: newPostingCursor(postings, len(ids)),
			weight: float64(repetitions) * ranking.IDF(len(i.ids), len(postings.ids)),
		})
	}
	slices.SortFunc(weighted, func(a, b weightedTerm) int { return strings.Compare(a.text, b.text) })
	top := ranking.NewTopK(k)
	for at, id := range ids {
		if at&1023 == 0 {
			if err := ctx.Err(); err != nil {
				return nil, err
			}
		}
		doc := i.docs[id]
		norm := ranking.LengthNorm(doc.length, average)
		var score float64
		for n := range weighted {
			term := &weighted[n]
			if match, found := term.cursor.seek(id); found {
				positions := term.cursor.list.ranges[match]
				score += ranking.Score(term.weight, positions.end-positions.start, norm)
			}
		}
		top.Add(ranking.Result{DocID: id, Title: doc.title, Score: score})
	}
	return top.Results(), nil
}
