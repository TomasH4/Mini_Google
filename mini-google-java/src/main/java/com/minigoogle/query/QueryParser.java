package com.minigoogle.query;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recursive‑descent parser that converts a token list into a QueryNode AST.
 * Supports explicit boolean operators (AND, OR, NOT) as well as implicit AND
 * when multiple terms appear without an operator (e.g. "scala java" -> scala AND java).
 *
 * Grammar:
 *   phrase   ::= "\"" WORD+ "\""
 *   term     ::= WORD | phrase
 *   factor   ::= term | NOT factor | '(' expr ')'
 *   andExpr  ::= factor ((AND | factor) factor)*
 *   orExpr   ::= andExpr (OR andExpr)*
 *   expr     ::= orExpr
 */
public class QueryParser {
    private final List<Tokenizer.Token> tokens;
    private int pos = 0;

    public QueryParser(List<Tokenizer.Token> tokens) {
        this.tokens = tokens;
    }

    public QueryNode parse() {
        if (tokens.isEmpty()) {
            return new TermNode("");
        }
        QueryNode node = parseOr();
        if (pos != tokens.size()) {
            throw new IllegalArgumentException("Unexpected token: " + peek());
        }
        return node;
    }

    private QueryNode parseOr() {
        QueryNode left = parseAnd();
        while (match(Tokenizer.Type.OR)) {
            QueryNode right = parseAnd();
            left = new OrNode(left, right);
        }
        return left;
    }

    private QueryNode parseAnd() {
        QueryNode left = parseFactor();
        while (match(Tokenizer.Type.AND) || canStartFactor()) {
            QueryNode right = parseFactor();
            left = new AndNode(left, right);
        }
        return left;
    }

    private boolean canStartFactor() {
        if (pos >= tokens.size()) return false;
        Tokenizer.Type t = tokens.get(pos).type();
        return t == Tokenizer.Type.TERM || t == Tokenizer.Type.PHRASE || t == Tokenizer.Type.NOT || t == Tokenizer.Type.LPAREN;
    }

    private QueryNode parseFactor() {
        if (match(Tokenizer.Type.NOT)) {
            return new NotNode(parseFactor());
        }
        if (match(Tokenizer.Type.LPAREN)) {
            QueryNode inner = parseOr();
            if (!match(Tokenizer.Type.RPAREN)) {
                throw new IllegalArgumentException("Missing closing parenthesis");
            }
            return inner;
        }
        if (match(Tokenizer.Type.PHRASE)) {
            Tokenizer.Token token = tokens.get(pos - 1);
            List<String> words = Arrays.stream(token.text().split("\\s+"))
                .map(w -> w.replaceAll("[^\\p{L}\\p{N}]+$", ""))
                .filter(w -> !w.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
            return new PhraseNode(words);
        }
        Tokenizer.Token token = expect(Tokenizer.Type.TERM);
        String text = token.text();
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 2) {
            String inner = text.substring(1, text.length() - 1);
            List<String> words = Arrays.stream(inner.split("\\s+"))
                .map(w -> w.replaceAll("[^\\p{L}\\p{N}]+$", ""))
                .filter(w -> !w.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
            return new PhraseNode(words);
        }
        return new TermNode(text.toLowerCase());
    }

    private boolean match(Tokenizer.Type type) {
        if (pos < tokens.size() && tokens.get(pos).type() == type) {
            pos++;
            return true;
        }
        return false;
    }

    private Tokenizer.Token expect(Tokenizer.Type type) {
        if (pos < tokens.size() && tokens.get(pos).type() == type) {
            return tokens.get(pos++);
        }
        throw new IllegalArgumentException("Expected token type " + type + " but got " + (pos < tokens.size() ? tokens.get(pos) : "EOF"));
    }

    private Tokenizer.Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }
}
