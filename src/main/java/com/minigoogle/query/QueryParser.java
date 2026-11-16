package com.minigoogle.query;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recursive‑descent parser that converts a token list into a QueryNode AST.
 * Grammar (precedence from highest to lowest):
 *   phrase   ::= "\"" WORD+ "\""
 *   term     ::= WORD | phrase
 *   factor   ::= term | NOT factor | '(' expr ')'
 *   andExpr  ::= factor (AND factor)*
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
        while (match(Tokenizer.Type.AND)) {
            QueryNode right = parseFactor();
            left = new AndNode(left, right);
        }
        return left;
    }

    // Updated factor parsing to handle PHRASE tokens and normalize words to lower case
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
        // Phrase token introduced by the tokenizer
        if (match(Tokenizer.Type.PHRASE)) {
            Tokenizer.Token token = tokens.get(pos - 1);
            // Split on whitespace, strip trailing punctuation, and lower‑case each word
            List<String> words = Arrays.stream(token.text().split("\\s+"))
                .map(w -> w.replaceAll("[^\\p{L}\\p{N}]+$", ""))
                .filter(w -> !w.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
            return new PhraseNode(words);
        }
        // Term token
        Tokenizer.Token token = expect(Tokenizer.Type.TERM);
        String text = token.text();
        // Legacy support: quoted text that ended up as TERM
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 2) {
            String inner = text.substring(1, text.length() - 1);
            List<String> words = Arrays.stream(inner.split("\\s+"))
                .map(w -> w.replaceAll("[^\\p{L}\\p{N}]+$", ""))
                .filter(w -> !w.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
            return new PhraseNode(words);
        }
        return new TermNode(text);
    }

    // Utility helpers
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
