// Package query parses boolean full-text queries.
package query

import (
	"errors"
	"fmt"
	"strings"
	"unicode"
	"unicode/utf8"
)

// Node is a term, an exact phrase, or a boolean expression.
type Node interface{ isQueryNode() }

type (
	Term   struct{ Text string }
	Phrase struct{ Text string }
	And    struct{ Left, Right Node }
	Or     struct{ Left, Right Node }
	Not    struct{ Child Node }
)

func (Term) isQueryNode()   {}
func (Phrase) isQueryNode() {}
func (And) isQueryNode()    {}
func (Or) isQueryNode()     {}
func (Not) isQueryNode()    {}

var ErrEmpty = errors.New("empty query")

const maxTokens = 1024

type kind uint8

const (
	end kind = iota
	term
	phrase
	open
	closeParen
	and
	or
	not
)

type token struct {
	kind
	text string
	pos  int
}

type lexer struct {
	text string
	pos  int
}

func (l *lexer) next() (token, error) {
	for l.pos < len(l.text) {
		r, n := utf8.DecodeRuneInString(l.text[l.pos:])
		if !unicode.IsSpace(r) {
			break
		}
		l.pos += n
	}
	start := l.pos
	if start == len(l.text) {
		return token{kind: end, pos: start}, nil
	}
	l.pos++
	switch l.text[start] {
	case '(':
		return token{open, "(", start}, nil
	case ')':
		return token{closeParen, ")", start}, nil
	case '"':
		offset := strings.IndexByte(l.text[l.pos:], '"')
		if offset < 0 {
			return token{}, fmt.Errorf("byte %d: unterminated phrase", start)
		}
		text := l.text[l.pos : l.pos+offset]
		l.pos += offset + 1
		if strings.TrimSpace(text) == "" {
			return token{}, fmt.Errorf("byte %d: empty phrase", start)
		}
		return token{phrase, text, start}, nil
	}

	l.pos = start
	for l.pos < len(l.text) {
		r, n := utf8.DecodeRuneInString(l.text[l.pos:])
		if unicode.IsSpace(r) || r == '(' || r == ')' || r == '"' {
			break
		}
		l.pos += n
	}
	text := l.text[start:l.pos]
	k := term
	switch {
	case strings.EqualFold(text, "AND"):
		k = and
	case strings.EqualFold(text, "OR"):
		k = or
	case strings.EqualFold(text, "NOT"):
		k = not
	}
	return token{k, text, start}, nil
}

type parser struct {
	lexer lexer
	token token
	count int
	err   error
}

func (p *parser) advance() {
	if p.err != nil {
		return
	}
	p.token, p.err = p.lexer.next()
	if p.token.kind != end {
		p.count++
		if p.count > maxTokens {
			p.err = fmt.Errorf("query exceeds %d tokens", maxTokens)
		}
	}
}

// Parse recognizes NOT > AND > OR, with implicit AND between adjacent operands.
func Parse(text string) (Node, error) {
	p := parser{lexer: lexer{text: text}}
	p.advance()
	if p.err == nil && p.token.kind == end {
		return nil, ErrEmpty
	}
	node := p.parseOr()
	if p.err != nil {
		return nil, p.err
	}
	if p.token.kind != end {
		return nil, fmt.Errorf("byte %d: unexpected %q", p.token.pos, p.token.text)
	}
	return node, nil
}

func (p *parser) parseOr() Node {
	left := p.parseAnd()
	for p.err == nil && p.token.kind == or {
		p.advance()
		left = Or{Left: left, Right: p.parseAnd()}
	}
	return left
}

func (p *parser) parseAnd() Node {
	left := p.factor()
	for p.err == nil {
		switch p.token.kind {
		case and:
			p.advance()
		case not, open, term, phrase:
			// Adjacent operands bind as AND.
		default:
			return left
		}
		left = And{Left: left, Right: p.factor()}
	}
	return left
}

func (p *parser) factor() Node {
	if p.err != nil {
		return nil
	}
	t := p.token
	switch t.kind {
	case term:
		p.advance()
		return Term{Text: t.text}
	case phrase:
		p.advance()
		return Phrase{Text: t.text}
	case not:
		p.advance()
		return Not{Child: p.factor()}
	case open:
		p.advance()
		node := p.parseOr()
		if p.err == nil && p.token.kind != closeParen {
			p.err = fmt.Errorf("byte %d: missing closing parenthesis", t.pos)
		}
		p.advance()
		return node
	default:
		p.err = fmt.Errorf("byte %d: expected an operand", t.pos)
		return nil
	}
}
