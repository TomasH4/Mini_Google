package query

import (
	"errors"
	"reflect"
	"strings"
	"testing"
)

func TestParse(t *testing.T) {
	want := Or{
		Left: Term{Text: "java"},
		Right: And{
			Left:  Or{Left: Term{Text: "scala"}, Right: Phrase{Text: "hilos virtuales"}},
			Right: Not{Child: Not{Child: Term{Text: "Go"}}},
		},
	}
	got, err := Parse(`java oR ((scala OR "hilos virtuales") NOT NOT Go)`)
	if err != nil || !reflect.DeepEqual(got, want) {
		t.Fatalf("Parse = %#v, %v; want %#v", got, err, want)
	}
}

func TestInvalidQueries(t *testing.T) {
	if _, err := Parse(" \r\n "); !errors.Is(err, ErrEmpty) {
		t.Fatalf("empty query error = %v", err)
	}
	for _, input := range []string{`""`, `" `, "()", "(x", "x)", "x AND", "OR x", "NOT", strings.Repeat("NOT ", maxTokens) + "x"} {
		t.Run(input[:min(len(input), 40)], func(t *testing.T) {
			if _, err := Parse(input); err == nil {
				t.Fatal("accepted invalid query")
			}
		})
	}
}

func BenchmarkParse(b *testing.B) {
	const text = `("índice invertido" OR trie) AND NOT (java scala)`
	b.ReportAllocs()
	b.ResetTimer()
	for range b.N {
		if _, err := Parse(text); err != nil {
			b.Fatal(err)
		}
	}
}
