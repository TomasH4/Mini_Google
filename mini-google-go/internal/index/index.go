// Package index owns the positional index and provides consistent full-text searches.
package index

import (
	"cmp"
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"sync"

	"minigoogle/internal/trie"
)

type Document struct {
	ID    int
	Title string
	Body  string
}

type document struct {
	title  string
	length int
}

type positionRange struct{ start, end int }

// IDs and ranges have matching offsets. Positions are stored in a single
// arena per term, so inserting an out-of-order ID only moves IDs and ranges.
type postingList struct {
	ids       []int
	ranges    []positionRange
	positions []int
}

func (p *postingList) add(id int, a *analyzer, g group, sorted bool) {
	start := len(p.positions)
	for at := g.first; at >= 0; at = a.words[at].next {
		p.positions = append(p.positions, a.words[at].position)
	}
	positions := positionRange{start, len(p.positions)}
	if sorted {
		p.ids = append(p.ids, id)
		p.ranges = append(p.ranges, positions)
		return
	}
	at, _ := slices.BinarySearch(p.ids, id)
	p.ids = slices.Insert(p.ids, at, id)
	p.ranges = slices.Insert(p.ranges, at, positions)
}

func (p *postingList) positionsAt(at int) []int {
	r := p.ranges[at]
	return p.positions[r.start:r.end]
}

var ErrDuplicateID = errors.New("duplicate document ID")

// The zero value is an empty index. An Index must not be copied after first use.
type Index struct {
	mu          sync.RWMutex
	docs        map[int]document
	terms       map[string]*postingList
	ids         []int
	totalLength int64
	vocabulary  trie.Trie
}

type Stats struct {
	Documents  int
	Vocabulary int
	Tokens     int64
}

func (i *Index) Stats() Stats {
	i.mu.RLock()
	defer i.mu.RUnlock()
	return Stats{len(i.ids), len(i.terms), i.totalLength}
}

// Build indexes documents already in memory. It leaves the input slice untouched.
func Build(ctx context.Context, docs []Document) (*Index, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	byID := func(a, b Document) int { return cmp.Compare(a.ID, b.ID) }
	if !slices.IsSortedFunc(docs, byID) {
		docs = slices.Clone(docs)
		slices.SortFunc(docs, byID)
	}
	i := &Index{
		docs:  make(map[int]document, len(docs)),
		terms: make(map[string]*postingList, min(len(docs), 512)),
		ids:   make([]int, 0, len(docs)),
	}
	a := newAnalyzer()
	for _, doc := range docs {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		if err := i.validateID(doc.ID); err != nil {
			return nil, err
		}
		length := a.scan(strings.TrimSpace(doc.Body))
		i.insert(doc, length, &a, true, false)
	}
	for term, postings := range i.terms {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		i.vocabulary.SetFrequency(term, len(postings.ids))
	}
	return i, nil
}

func (i *Index) validateID(id int) error {
	if id <= 0 {
		return fmt.Errorf("document ID must be positive: %d", id)
	}
	if _, exists := i.docs[id]; exists {
		return fmt.Errorf("%w: %d", ErrDuplicateID, id)
	}
	return nil
}

// Add publishes a document and its postings together, after analyzing text outside the write lock.
func (i *Index) Add(ctx context.Context, doc Document) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	a := newAnalyzer()
	length := a.scan(strings.TrimSpace(doc.Body))
	i.mu.Lock()
	defer i.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := i.validateID(doc.ID); err != nil {
		return err
	}
	if i.docs == nil {
		i.docs = make(map[int]document)
		i.terms = make(map[string]*postingList)
	}
	i.insert(doc, length, &a, len(i.ids) == 0 || doc.ID > i.ids[len(i.ids)-1], true)
	return nil
}

func (i *Index) insert(doc Document, length int, a *analyzer, sorted, updateVocabulary bool) {
	i.docs[doc.ID] = document{title: strings.Clone(doc.Title), length: length}
	i.totalLength += int64(length)
	if sorted {
		i.ids = append(i.ids, doc.ID)
	} else {
		at, _ := slices.BinarySearch(i.ids, doc.ID)
		i.ids = slices.Insert(i.ids, at, doc.ID)
	}
	for term, g := range a.groups {
		postings := i.terms[term]
		if postings == nil {
			// Keep the term without retaining the input document's backing string.
			term = strings.Clone(term)
			postings = &postingList{}
			i.terms[term] = postings
		}
		postings.add(doc.ID, a, g, sorted)
		if updateVocabulary {
			i.vocabulary.SetFrequency(term, len(postings.ids))
		}
	}
}

func (i *Index) Suggest(ctx context.Context, prefix string, limit int) ([]trie.Suggestion, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	i.mu.RLock()
	defer i.mu.RUnlock()
	return i.vocabulary.Suggest(ctx, strings.ToLower(strings.TrimSpace(prefix)), limit)
}

func (i *Index) Fuzzy(ctx context.Context, term string, distance, limit int) ([]trie.Suggestion, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	i.mu.RLock()
	defer i.mu.RUnlock()
	return i.vocabulary.Fuzzy(ctx, normalize(term), distance, limit)
}
