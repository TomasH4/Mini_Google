// Tokenizador que reconoce paréntesis, operadores lógicos, términos y frases entre comillas.
package com.minigoogle.query;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokeniza la entrada reconociendo:
 *   - '(' y ')'
 *   - operadores AND, OR, NOT (case‑insensitive)
 *   - palabras (términos) compuestas solo por letras o dígitos
 *   - frases entre comillas dobles, que pueden contener espacios y puntuación.
 *
 * Los caracteres de puntuación que no forman parte de una frase (.,;:!?) se tratan como separadores y se descartan.
 */
public class Tokenizer {
    public enum Type { LPAREN, RPAREN, AND, OR, NOT, TERM, PHRASE }
    public static record Token(Type type, String text) {}

    public static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            switch (c) {
                case '(':
                    flush(sb, tokens);
                    tokens.add(new Token(Type.LPAREN, "("));
                    break;
                case ')':
                    flush(sb, tokens);
                    tokens.add(new Token(Type.RPAREN, ")"));
                    break;
                case '"': // inicio de frase
                    flush(sb, tokens);
                    int start = i + 1;
                    int end = input.indexOf('"', start);
                    if (end == -1) {
                        end = len; // sin comilla de cierre, tomar hasta el final
                    }
                    String phrase = input.substring(start, end);
                    tokens.add(new Token(Type.PHRASE, phrase));
                    i = end; // avanzar al carácter de cierre (o fin)
                    break;
                case ' ': case '\t': case '\n': case '\r':
                    // separador de espacio
                    flush(sb, tokens);
                    break;
                default:
                    // Si es letra o número lo acumulamos, si no es puntuación lo usamos como separador
                    if (Character.isLetterOrDigit(c)) {
                        sb.append(c);
                    } else {
                        // cualquier otro carácter (.,;:!?) sirve como delimitador
                        flush(sb, tokens);
                    }
            }
        }
        flush(sb, tokens);
        // Convertir términos que coinciden con operadores en sus tokens propios
        List<Token> finalTokens = new ArrayList<>();
        for (Token t : tokens) {
            if (t.type == Type.TERM) {
                String up = t.text.toUpperCase();
                switch (up) {
                    case "AND": finalTokens.add(new Token(Type.AND, up)); continue;
                    case "OR":  finalTokens.add(new Token(Type.OR, up));  continue;
                    case "NOT": finalTokens.add(new Token(Type.NOT, up)); continue;
                }
            }
            finalTokens.add(t);
        }
        return finalTokens;
    }

    private static void flush(StringBuilder sb, List<Token> tokens) {
        if (sb.length() > 0) {
            tokens.add(new Token(Type.TERM, sb.toString()));
            sb.setLength(0);
        }
    }
}
