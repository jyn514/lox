package lox.java.lexer;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import static lox.java.Lox.error;
import static lox.java.lexer.Token.Type.*;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    // current should ONLY be modified by advance() (since it updates column)
    private int start = 0, current = 0, line = 1, column = 0;
    private int errorStart = -1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!atEnd()) {
            Token t = scanToken();
            if (t != null) tokens.add(t);
        }
        return tokens;
    }

    /*
     * Returns the next token.
     * If the next token is illegal, or if there is no next token, return null.
     */
    private Token scanToken() {
        char c = advance();
        switch (c) {
            case '(': return makeToken(LEFT_PAREN);
            case ')': return makeToken(RIGHT_PAREN);
            case '{': return makeToken(LEFT_BRACE);
            case '}': return makeToken(RIGHT_BRACE);
            case ',': return makeToken(COMMA);
            case '.': return makeToken(DOT);
            case ';': return makeToken(SEMICOLON);

            case '!': return makeToken(match('=') ? BANG_EQUAL : BANG);
            case '=': return makeToken(match('=') ? EQUAL_EQUAL : EQUAL);
            case '<': return makeToken(match('=') ? LESS_EQUAL : LESS);
            case '>': return makeToken(match('=') ? GREATER_EQUAL : GREATER);
            case '*': return makeToken(match('=') ? STAR_EQUAL : STAR);
            case '/':
                if (match('/')) {
                    flushError();
                    while(!atEnd() && advance() != '\n') {}
                    if (source.charAt(current - 1) == '\n') {
                        line++;
                        start = current;
                        return scanToken();
                    }
                    // atEnd
                    return null;
                }
                return makeToken(match('=') ? SLASH_EQUAL : SLASH);

            case '+': return makeToken(match('=') ? PLUS_EQUAL :
                                       match('+') ? PLUS_PLUS : PLUS);
            case '-': return makeToken(match('=') ? MINUS_EQUAL :
                                       match('-') ? MINUS_MINUS : MINUS);

            // ignore whitespace
            case '\n':
                line++;
            case ' ':
            case '\r':
            case '\t':
                flushError();
                if (atEnd()) return null;
                start = current;
                return scanToken();

            case '"':
                flushError();
                return makeToken(STRING, string());

            default:
                return handleErrors(c);
        }
    }

    private String string () {
        StringBuilder result = new StringBuilder();

        while (!atEnd() && peek() != '"') {
            if (peek() == '\n') {
                error(line++, column, "Unterminated string: expected '\"', got <end of line>");
                return result.toString();
            }
            result.append(unescape(advance()));
        }

        if (atEnd()) {
            error(line, column, "Unterminated string: expected '\"', got <end of file>");
            return result.toString();
        }

        advance();  // closing "
        return source.substring(start + 1, current - 1);
    }

    private char peek() {
        return source.charAt(current);
    }

    private char advance() {
        column++;
        return source.charAt(current++);
    }

    private boolean match(char c) {
        if (!atEnd() && source.charAt(current) == c) {
            advance();
            return true;
        }
        return false;
    }

    private boolean atEnd() {
        return current >= source.length();
    }

    private Token makeToken(Token.Type type) {
        return makeToken(type, null);
    }

    private Token makeToken(Token.Type type, Object value) {
        flushError();
        Token result = new Token(type, source.substring(start, current), line, column, value);
        start = current;
        return result;
    }

    private void flushError() {
        if (errorStart != -1) {
            error(line, errorStart, "Illegal token '"
                  + source.substring(errorStart, start) + '\'');
        }
        errorStart = -1;
    }

    /*
     * Returns the next valid token, knowing that the character `c` is invalid.
     * If there are no more valid tokens, returns null.
     *
     * Assumes that `c' came from source[current - 1].
     *
     * No matter how many times it is called,
     * only sends one error per sequence of illegal characters.
     *
     * Examples:
     * > `(
     * 1:0: error: Illegal token '`'
     * LEFT_PAREN ( null
     * > (`
     * 1:1: error: Illegal token '`'
     * LEFT_PAREN ( null
     * > ``((``(
     * 1:0: error: Illegal token '``'
     * 1:4: error: Illegal token '``'
     * LEFT_PAREN ( null
     * LEFT_PAREN ( null
     * LEFT_PAREN ( null
     */
    private Token handleErrors(char c) {
        if (errorStart == -1) errorStart = start;
        start = current;

        // return null HAS to come before recursive call (stopping condition)
        if (atEnd()) {
            flushError();
            return null;
        }

        // start looking for a valid token at the current index
        // scanToken HAS to come before error (it's how we know how big an illegal token is)
        Token result = scanToken();
        flushError();
        return result;
    }
}
