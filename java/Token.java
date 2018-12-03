package lox.java;

public class Token {
    final Type type;
    final String lexeme;
    final Object value;
    final int line, column;

    Token(Type type, String lexeme, int line, int column) {
        this(type, lexeme, line, column, null);
    }

    Token(Type type, String lexeme, int line, int column, Object value) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
        this.value = value;
    }

    public String toString() {
        return this.type + " " + this.lexeme + ' ' + this.value;
    }

    public enum Type {
        // single characters
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
        COMMA, DOT, SEMICOLON,

        // one or two characters
        PLUS, PLUS_EQUAL, PLUS_PLUS,
        MINUS, MINUS_EQUAL, MINUS_MINUS,
        SLASH, SLASH_EQUAL, SLASH_SLASH,
        STAR, STAR_EQUAL,
        BANG, BANG_EQUAL,
        EQUAL, EQUAL_EQUAL,
        GREATER, GREATER_EQUAL,
        LESS, LESS_EQUAL,

        // literals
        IDENTIFIER, STRING, NUMBER,

        // keywords
        AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
        PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,
    }
}
