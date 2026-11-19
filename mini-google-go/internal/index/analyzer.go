package index

import (
	"strings"
	"unicode"
)

// normalize keeps punctuation inside a token and preserves Unicode letters and numbers.
func normalize(word string) string {
	return strings.ToLower(strings.TrimFunc(word, func(r rune) bool {
		return !unicode.IsLetter(r) && !unicode.IsNumber(r)
	}))
}

// scanWords counts whitespace-delimited positions even when edge cleaning removes a token.
func scanWords(text string, visit func(string, int)) int {
	count := 0
	for end := 0; end < len(text); {
		for end < len(text) && space(text[end]) {
			end++
		}
		start := end
		for end < len(text) && !space(text[end]) {
			end++
		}
		if start != end {
			word := normalize(text[start:end])
			if word != "" {
				visit(word, count)
			}
			count++
		}
	}
	return count
}

func space(c byte) bool {
	return c == ' ' || c >= '\t' && c <= '\r'
}

type occurrence struct {
	position int
	next     int
}

type group struct {
	first, last int
}

// A document's groups share one scratch slice, reused across the bulk build.
type analyzer struct {
	groups map[string]group
	words  []occurrence
}

func newAnalyzer() analyzer {
	return analyzer{groups: make(map[string]group, 64), words: make([]occurrence, 0, 128)}
}

func (a *analyzer) scan(text string) int {
	clear(a.groups)
	a.words = a.words[:0]
	return scanWords(text, func(word string, pos int) {
		at := len(a.words)
		g, found := a.groups[word]
		if found {
			a.words[g.last].next = at
			g.last = at
		} else {
			g = group{first: at, last: at}
		}
		a.groups[word] = g
		a.words = append(a.words, occurrence{position: pos, next: -1})
	})
}
