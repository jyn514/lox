package lox.java;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static lox.java.Token.Type.*;
import static lox.java.Lox.error;
import static lox.java.LoxType.TypeError;

/*
 * Recursive descent top-down AST parser.
 * Methods called sooner have lower precedence than later methods.
 */
class Parser extends Pass<List<Token>, List<Stmt>> {
  private final List<Stmt> result = new ArrayList<>();
  private int current = 0;

  private static final Token.Type[] DECLARATORS = { VOID, INT, BOOL, STRING_TYPE, DOUBLE };

  public Parser(List<Token> input) {
    super(input);
  }

  /* program ::= varDeclaration* */
  public List<Stmt> runPass() {
    // cache result if we've already parsed the input
    if (current != 0) return result;

    while (!atEnd()) {
      try {
        Stmt stmt = declaration();
        if (stmt != null) result.add(stmt);
      } catch (ParseError e) {}
    }
    return result;
  }

  /* STATEMENTS */

  /* declaration = funDeclaration | varDeclaration | statement */
  private Stmt declaration() throws ParseError {
    if (match(DECLARATORS)) {
      Token type = previous(), id = consume(IDENTIFIER);
      if (match(LEFT_PAREN)) return funDeclaration(type, id);
      return varDeclaration(type, id);
    }
    return statement();
  }

  /* funDeclaration :: type identifier "(" ( type identifier "," )* ") block */
  private Stmt.Function funDeclaration(Token type, Token identifier) throws ParseError {
    final Expr.Symbol func = new Expr.Symbol(identifier,
      LoxType.get(type.type));
    List<Expr.Symbol> arguments = new ArrayList<>();
    while (match(INT, BOOL, STRING_TYPE, DOUBLE)) {
      type = previous();
      arguments.add(new Expr.Symbol(consume(IDENTIFIER), LoxType.get(type.type)));
      if (peek() != null && peek().type != COMMA) break;
      consume(COMMA);
    }
    func.arity = arguments.size();
    consume(RIGHT_PAREN);
    consume(LEFT_BRACE);
    return new Stmt.Function(func, arguments, block());
  }

  /* varDeclaration ::= type identifier ("=" expression)? ";" */
  private Stmt.Var varDeclaration(Token type, Token identifier) throws ParseError {
    Expr.Symbol variable = new Expr.Symbol(identifier, LoxType.get(type.type));
    Expr.Assign equals = null;

    if (match(EQUAL)) {
      equals = new Expr.Assign(variable, previous(), expression(), variable.type);
    }
    consume(SEMICOLON);
    return new Stmt.Var(variable, equals);
  }

  /* statement ::= exprStmt | printStmt | block
   *               | if | while | for | break | continue | ';' */
  private Stmt statement() throws ParseError {
    if (match(PRINT)) return printStatement();
    if (match(LEFT_BRACE)) return block();
    if (match(IF)) return ifStatement();
    if (match(WHILE)) return whileStatement();
    if (match(FOR)) return forStatement();
    if (match(BREAK, CONTINUE)) {
      Stmt.LoopControl result =  new Stmt.LoopControl(previous());
      consume(SEMICOLON);
      return result;
    }
    if (match(SEMICOLON)) return null;

    return expressionStatement();
  }

  /* block ::= '{' declaration* '}' */
  private Stmt.Block block() throws ParseError {
    List<Stmt> statements = new ArrayList<>();

    while (!atEnd() && (peek().type != RIGHT_BRACE)) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE);
    return new Stmt.Block(statements);
  }

  /* if ::= "if" '(' expression ') statement ( "else" statement )?
   *
   * note that we eagerly find the else, so 'dangling elses'
   * are parsed as if they belonged to the innermost 'if'. */
  private Stmt.If ifStatement() throws ParseError {
    Expr condition = condition();
    Stmt then = statement();
    Stmt otherwise = null;
    if (match(ELSE)) {
      otherwise = statement();
    }

    return new Stmt.If(condition, then, otherwise);
  }

  private Stmt.While whileStatement() throws ParseError {
    Expr condition = condition();
    Stmt body = statement();
    return new Stmt.While(condition, body);
  }

  private Stmt forStatement() throws ParseError {
    Stmt declaration = null;
    Expr condition = null;
    Expr after = null;

    consume(LEFT_PAREN);
    if (!match(SEMICOLON)) {
      if (match(INT, DOUBLE, BOOL, STRING_TYPE)) declaration = declaration();
      else declaration = expressionStatement();
    }
    if (!match(SEMICOLON))
      condition = expressionStatement().expression;
    if (!match(RIGHT_PAREN)) {
      after = expression();
      consume(RIGHT_PAREN);
    }

    Stmt body = statement();
    /*
     * desugar the for loop
     *
     * for (int i = 0; i < 10; i++) { print(i); }
     *
     * is equivalent to
     *
     * {
     *  int i = 0;
     *  while (i < 10) {
     *      print(i);
     *      i++;
     *  }
     * }
     */
    if (after != null) {
      body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(after)));
    }
    if (condition == null) condition = new Expr.Literal(true, LoxType.BOOL);
    Stmt.While whileLoop = new Stmt.While(condition, body);

    if (declaration == null) return whileLoop;

    return new Stmt.Block(Arrays.asList(declaration, whileLoop));
  }

  /* printStmt ::= "print" expression ";" ; */
  private Stmt.Print printStatement() throws ParseError {
    Expr value = expression();
    consume(SEMICOLON);
    return new Stmt.Print(value);
  }

  /* exprStmt ::= expression ";" ; */
  private Stmt.Expression expressionStatement() throws ParseError {
    Expr value = expression();
    consume(SEMICOLON);
    return new Stmt.Expression(value);
  }


  /* EXPRESSIONS */

  /* only used by statements */
  private Expr condition() throws ParseError {
    consume(LEFT_PAREN);
    Expr condition = expression();
    consume(RIGHT_PAREN);
    return condition;
  }

  /*
   * expression ::= comma
   */
  private Expr expression() throws ParseError {
    return comma();
  }

  /* comma ::= expression ',' expression */
  private Expr comma() throws ParseError {
    return parseBinary(this::assignment, null, COMMA);
  }

  /* assignment ::= expression ('=' expression)?
   * Note that we don't call parseBinary because assignment is right-associative */
  private Expr assignment() throws ParseError {
    Expr lvalue = or();
    if (match(EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL, DOT_EQUAL,
          CARET_EQUAL, PERCENT_EQUAL, AMPERSAND_EQUAL, PIPE_EQUAL)) {
      Token equals = previous();
      Expr rvalue = assignment();
      if (!(lvalue instanceof Expr.Symbol)) {
        error(equals.line, equals.column, "INTERNAL error: pointers not implemented");
      }
      return new Expr.Assign((Expr.Symbol)lvalue, equals, rvalue, rvalue.type);
    }
    return lvalue;
  }

  /* or ::= and ("or" and)* */
  private Expr or() throws ParseError {
    Expr left = and();
    while (match(OR)) {
      if (left.type != LoxType.BOOL) throwTypeError(left.type, previous(), LoxType.BOOL);
      left = new Expr.Logical(left, previous(), and(), LoxType.BOOL);
    }
    return left;
  }

  /* and ::= equality ("and" equality)* */
  private Expr and() throws ParseError {
    Expr left = equality();
    while (match(AND)) {
      if (left.type != LoxType.BOOL) throwTypeError(left.type, previous(), LoxType.BOOL);
      left = new Expr.Logical(left, previous(), equality(), LoxType.BOOL);
    }
    return left;
  }

  /*
   * equality ::= comparison ( ( "!=" | "==" ) comparison )*
   */
  private Expr equality() throws ParseError {
    return parseBinary(this::comparison, null, BANG_EQUAL, EQUAL_EQUAL);
  }

  /* comparison ::= bitwise ( ( ">" | ">=" | "<" | "<=" ) bitwise )* ; */
  private Expr comparison() throws ParseError {
    return parseBinary(this::bitwise, LoxType.DOUBLE, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
  }

  /* bitwise ::= addition ( ( "^" | "|" | "&" ) addition )* ; */
  private Expr bitwise() throws ParseError {
    return parseBinary(this::addition, LoxType.INT, CARET, PIPE, AMPERSAND);
  }

  /* addition ::= multiplication ( ( "-" | "+" ) multiplication )* ; */
  private Expr addition() throws ParseError {
    return parseBinary(this::multiplication, LoxType.DOUBLE, MINUS, PLUS);
  }

  /* multiplication ::= unary ( ( "/" | "*" | "%" ) unary )* ; */
  private Expr multiplication() throws ParseError {
    return parseBinary(this::prefixUnary, LoxType.DOUBLE, SLASH, STAR, PERCENT);
  }

  /* prefixUnary ::= ( "!" | "-" | "++" | "--" ) prefixUnary | postfixUnary ; */
  private Expr prefixUnary() throws ParseError {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr expr = prefixUnary();

      // TODO: this is a mess, clean it up when we implement type promotion
      if ((operator.type != BANG
            && expr.type != LoxType.INT && expr.type != LoxType.DOUBLE)
          || (operator.type == BANG && expr.type != LoxType.BOOL))
        error(operator.line, operator.column,
            "Illegal type " + expr.type
            + " for unary operator " + operator);
      return new Expr.Unary(operator, expr, expr.type);
    }
    /*
       if (match(PLUS_PLUS, MINUS_MINUS)) {
       boolean plus = previous().type == PLUS_PLUS;
       return
       }
       */

    return postfixUnary();
  }

  /* postfixUnary ::= call ( "++" | "--" )? */
  private Expr postfixUnary() throws ParseError {
    Expr result = call();
    /*
       if (!match(PLUS_PLUS, MINUS_MINUS)) return result;
       Token operator = previous().type == PLUS_PLUS ?
       new Token(PLUS, "+", -1, -1) :
       new Token(MINUS, "-", -1, -1);
       return new Expr.Assign(result, new Token(EQUAL, "=", -1, -1),
       new Expr.Binary(result, operator, new Expr.Literal(1.0)));
       */
    return result;
  }

  /* call ::= primary ( "(" arguments* ")" )* */
  private Expr call() throws ParseError {
    Expr primary = primary();
    while (match(LEFT_PAREN)) {
      if (!(primary instanceof Expr.Symbol)) {
        error(previous().line, previous().column, "INTERNAL error: "
            + "first class functions and function pointers not implemented");
      }
      // we need to look up the type of this function in the symbol table
      if (match(RIGHT_PAREN)) {
        primary = new Expr.Call((Expr.Symbol)primary, previous(),
            new ArrayList<>());
      } else {
        primary = new Expr.Call((Expr.Symbol)primary, previous(), arguments());
        consume(RIGHT_PAREN);
      }
    }
    return primary;
  }

  /* arguments ::= assignment ( "," assignment )* */
  private List<Expr> arguments() throws ParseError {
    List<Expr> result = new ArrayList<>();
    result.add(assignment());

    while (match(COMMA)) {
      result.add(expression());
    }

    return result;
  }

  /* primary ::= NUMBER | STRING | "false" | "true" | "null" | "(" expression ")" ; */
  private Expr primary() throws ParseError {
    if (match(FALSE)) return new Expr.Literal(false, LoxType.BOOL);
    if (match(TRUE)) return new Expr.Literal(true, LoxType.BOOL);
    if (match(NULL)) return new Expr.Literal(null, LoxType.NULL);
    if (match(IDENTIFIER)) return new Expr.Symbol(previous(), null);

    if (match(NUMBER)) {
      return new Expr.Literal(previous().value,
        previous().value instanceof Integer ? LoxType.INT : LoxType.DOUBLE);
    }

    if (match(STRING)) {
      String value = previous().value.toString();
      if (match(STRING)) {
        value += previous().value;
      }
      return new Expr.Literal(value, LoxType.STRING);
    }

    // groupings
    if (match(LEFT_PAREN)) {
      // empty parentheses
      if (match(RIGHT_PAREN)) return new Expr.Grouping(null, LoxType.UNDEFINED);
      Expr expr = expression();
      consume(RIGHT_PAREN);
      return new Expr.Grouping(expr, expr.type);
    }

    if (peek() == null) {
      error(previous().line, previous().column + previous().lexeme.length(),
          "Unexpected end of file");
    } else {
      error(peek().line, peek().column,
          "Unhandled token " + peek().type);
      //advance();
      panic();
    }
    throw new ParseError();
  }

  /* panic mode; advance until we're sure the expression is over */
  private void panic() {
    advance();

    while (!atEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUNCTION:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }

  private Expr parseBinary(BinaryExprParser function, LoxType max,
      Token.Type ... input) throws ParseError {
    Expr result = function.parse();
    while (match(input)) {
      Token operator = previous();
      Expr right = function.parse();
      // see https://docs.oracle.com/javase/9/docs/api/java/util/Set.html#toArray-T:A-
      assertPromotable(result.type, operator, right.type, max);
      result = new Expr.Binary(result, operator, right, result.type);
    }
    return result;
  }

  private void throwTypeError(LoxType left, Token operator, LoxType right) {
      error(operator.line, operator.column,
          "Illegal type for operator '" + operator.type + "': "
          + left + " and " + right);
  }

  private void assertPromotable(LoxType left, Token operator,
                                   LoxType right, LoxType max) {
    try {
      LoxType.assertPromotable(left, right, max);
    } catch (TypeError e) {
      throwTypeError(left, operator, right);
    }
  }

  /*
   * Meant to be used as an anonymous class with lambdas.
   */
  private interface BinaryExprParser {
    Expr parse() throws ParseError;
  }


  private Token consume(Token.Type type) {
    if (atEnd() || peek().type != type) {
      error(previous().line, previous().column,
          "Expected " + type + "; got "
          + (atEnd() ? "<end-of-file>" : peek().lexeme));
      return null;
    } else return advance();
  }

  private boolean match(Token.Type ... types) {
    for (Token.Type type : types) {
      if (peek() != null && peek().type == type) {
        advance();
        return true;
      }
    }
    return false;
  }

  private Token peek() {
    return atEnd() ? null : input.get(current);
  }

  private Token advance() {
    return input.get(current++);
  }

  private Token previous() {
    return input.get(current - 1);
  }

  private boolean atEnd() {
    return current >= input.size();
  }

  @SuppressWarnings("serial")
  private static class ParseError extends Exception {}
}
