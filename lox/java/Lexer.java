package lox.java;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Map.entry;
import static lox.java.Lox.error;
import static lox.java.Token.Type.*;

class Lexer extends Pass<String, List<Token>> {
  private static final Map<Character, Character> escape_characters = Map.ofEntries(
    entry('\'', '\''),
    entry('"', '"'),
    entry('\\', '\\'),
    entry('0', '\0'),
    entry('b', '\b'),
    entry('f', '\f'),
    entry('n', '\n'),
    entry('r', '\r'),
    entry('t', '\t')
  );
  private static final Map<String, Token.Type> keywords = Map.ofEntries(
    entry("and",    AND),
    entry("class",  CLASS),
    entry("else",   ELSE),
    entry("false",  FALSE),
    entry("for",    FOR),
    entry("if",     IF),
    entry("null",   NULL),
    entry("or",     OR),
    entry("print",  PRINT),
    entry("return", RETURN),
    entry("super",  SUPER),
    entry("this",   THIS),
    entry("true",   TRUE),
    entry("var",    VAR),
    entry("while",  WHILE),
    entry("break", BREAK),
    entry("continue", CONTINUE),

    entry("bool", BOOL),
    entry("int", INT),
    entry("double", DOUBLE),
    entry("string", STRING_TYPE),
    entry("void", VOID)
  );

  private final List<Token> tokens = new ArrayList<>();
  // current should ONLY be modified by advance() (since it updates column and line)
  private int start = 0, current = 0, line = 1, column = 0;
  private int errorStart = -1;

  public Lexer(String input) {
    super(input);
  }

  public List<Token> runPass() {
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
    if (isDigit(c) || (c == '.' && isDigit(peek()))) {
      return makeToken(NUMBER, number());
    }
    switch (c) {
      case '(': return makeToken(LEFT_PAREN);
      case ')': return makeToken(RIGHT_PAREN);
      case '{': return makeToken(LEFT_BRACE);
      case '}': return makeToken(RIGHT_BRACE);
      case ',': return makeToken(COMMA);
      case ';': return makeToken(SEMICOLON);

      case '.': return makeToken(match('=') ? DOT_EQUAL : DOT);
      case '^': return makeToken(match('=') ? CARET_EQUAL : CARET);
      case '%': return makeToken(match('%') ? PERCENT_EQUAL : PERCENT);
      case '!': return makeToken(match('=') ? BANG_EQUAL : BANG);
      case '=': return makeToken(match('=') ? EQUAL_EQUAL : EQUAL);
      case '<': return makeToken(match('=') ? LESS_EQUAL : LESS);
      case '>': return makeToken(match('=') ? GREATER_EQUAL : GREATER);
      case '*': return makeToken(match('=') ? STAR_EQUAL : STAR);
      case '/':
        if (match('/')) {
          flushError();
          while(!atEnd() && advance() != '\n');
          if (previous() == '\n') {
            start = current;
            return scanToken();
          }
          // atEnd
          return null;
        } else if (match('*')) {
          flushError();
          while (!atEnd() && advance() != '*');
          if (previous() == '*' && match('/')) {
            advance();
            start = current;
            return atEnd() ? null : scanToken();
          }
        }
        return makeToken(match('=') ? SLASH_EQUAL : SLASH);

      case '+': return makeToken(match('=') ? PLUS_EQUAL :
                    match('+') ? PLUS_PLUS : PLUS);
      case '-': return makeToken(match('=') ? MINUS_EQUAL :
                    match('-') ? MINUS_MINUS : MINUS);
      case '&': return makeToken(match('=') ? AMPERSAND_EQUAL : AMPERSAND);
      case '|': return makeToken(match('=') ? PIPE_EQUAL : PIPE);

      // ignore whitespace
      case '\n':
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
        if (isAlpha(c)) return makeToken(identifier());
        return handleErrors();
    }
  }

  private String string() {
    boolean multiLine = false;
    if (match('"')) {
      if (match('"')) multiLine = true;
      else return "";
    }
    StringBuilder result = new StringBuilder();
    boolean escaped = false;
    String end = multiLine ? "\"\"\"" : "\"";
    Supplier<Boolean> endCondition = multiLine ? () -> input.length() <= current + 2
        || (peek() == '\"' && peekNext() == '\"' && input.charAt(current + 2) == '\"')
     : () -> input.length() <= current || peek() == '"';

    while (escaped || !endCondition.get()) {
      if (!multiLine && peek() == '\n') {
        error(line, column, "Unterminated string: expected '\"', got <end of line>");
        return result.toString();
      }
      char c = advance();
      if (escaped) {
        if (!escape_characters.containsKey(c)) {
          error(line, column, "Illegal escape character: " + c);
          // treat this as a literal backslash followed by c
          result.append("\\");
        } else {
          c = escape_characters.get(c);
        }
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
        continue;
      }
      result.append(c);
    }

    if (atEnd()) {
      error(line, column, "Unterminated string: expected '" + end + "', got <end of file>");
    } else {
      advance();  // closing "
      if (multiLine) { // closing """
        if (input.length() <= current + 1) {
          error(line, column, "Unterminated string: expected '" + end + "', got " + input.substring(current) + "<end of file>");
        } else {
          advance();
          advance();
        }
      }
    }
    return result.toString();
  }

  private Number number() {
    while (isDigit(peek())) advance();

    // only count '.' as part of a number if followed by a digit
    if (peek() == '.' && isDigit(peekNext())) {
      // consume the dot
      advance();
      while (isDigit(peek())) advance();
      return Double.parseDouble(input.substring(start, current));
    }

    return Integer.parseInt(input.substring(start, current));

  }

  private Token.Type identifier() {
    char c;
    while ((c = peek()) != 0 && (isAlphaNumeric(c) || c == '\'' || c == '?' || c == '_')) {
      advance();
    }
    return keywords.getOrDefault(input.substring(start, current), IDENTIFIER);
  }

  private char previous() {
    return current != 0 ? input.charAt(current - 1) : '\0';
  }

  private char peek() {
    return current >= input.length() ? '\0' : input.charAt(current);
  }

  private char peekNext() {
    return current + 1 >= input.length() ? '\0' : input.charAt(current + 1);
  }

  private char advance() {
    column++;
    char ret = input.charAt(current++);
    if (ret == '\n') {
      line++;
      column = 0;
    }
    return ret;
  }

  private boolean match(char c) {
    if (!atEnd() && input.charAt(current) == c) {
      advance();
      return true;
    }
    return false;
  }

  private boolean atEnd() {
    return current >= input.length();
  }

  private boolean isDigit(char c) {
    return '0' <= c && c <= '9';
  }

  private boolean isAlpha(char c) {
    return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
  }

  private boolean isAlphaNumeric(char c) {
    return isDigit(c) || isAlpha(c);
  }

  private Token makeToken(Token.Type type) {
    return makeToken(type, null);
  }

  private Token makeToken(Token.Type type, Object value) {
    flushError();
    Token result = new Token(type, input.substring(start, current), line,
        column - (current - start) + 1, value);
    start = current;
    return result;
  }

  private void flushError() {
    if (errorStart != -1) {
      error(line, errorStart, "Illegal token '"
          + input.substring(errorStart, start) + '\'');
    }
    errorStart = -1;
  }

  /*
   * Returns the next valid token, knowing that the character `c` is invalid.
   * If there are no more valid tokens, returns null.
   *
   * Assumes that `c' came from input[current - 1].
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
  private Token handleErrors() {
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
