package lox.java;

public class Token {
  final Type type;
  // TODO: should be final, need to find a way to mangle names without changing original
  String lexeme;
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
    COMMA, SEMICOLON,

    // one, two, or three characters
    PLUS, PLUS_EQUAL, PLUS_PLUS,
    MINUS, MINUS_EQUAL, MINUS_MINUS,
    SLASH, SLASH_EQUAL, SLASH_SLASH,
    STAR, STAR_EQUAL,
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    DOT, DOT_EQUAL,
    CARET, CARET_EQUAL,
    PERCENT, PERCENT_EQUAL,
    AMPERSAND, AMPERSAND_EQUAL,
    PIPE, PIPE_EQUAL,

    // literals
    IDENTIFIER, STRING, NUMBER,

    // keywords
    AND, CLASS, ELSE, FALSE, FUNCTION, FOR, IF, NULL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE, BREAK, CONTINUE,
    VOID,

    // primitive types
    INT, DOUBLE, BOOL, STRING_TYPE
  }
}
