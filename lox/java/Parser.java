package lox.java;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static java.util.Map.entry;
import static lox.java.Token.Type.*;
import static lox.java.Lox.error;

/*
 * Recursive descent top-down AST parser.
 * Methods called sooner have lower precedence than later methods.
 */
class Parser extends Pass<List<Token>, List<Stmt>> {
  private final List<Stmt> result = new ArrayList<>();
  private final Map<Token.Type, Token.Type> enhancedAssignment = Map.ofEntries(
    entry(BANG_EQUAL, BANG),
    entry(DOT_EQUAL, DOT),
    entry(PIPE_EQUAL, PIPE),
    entry(AMPERSAND_EQUAL, AMPERSAND),
    entry(PLUS_EQUAL, PLUS),
    entry(MINUS_EQUAL, MINUS),
    entry(STAR_EQUAL, STAR),
    entry(SLASH_EQUAL, SLASH),
    entry(CARET_EQUAL, CARET),
    entry(PERCENT_EQUAL, PERCENT)
  );

  private int current = 0;
  private boolean inBlock = false, inFunctionDeclaration = false;

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
    inFunctionDeclaration = true;

    final Expr.Symbol func = new Expr.Symbol(-1, identifier, LoxType.get(type.type));
    List<Expr.Symbol> arguments = new ArrayList<>();
    while (match(INT, BOOL, STRING_TYPE, DOUBLE)) {
      type = previous();
      arguments.add(new Expr.Symbol(-1, consume(IDENTIFIER), LoxType.get(type.type)));
      if (peek() != null && peek().type != COMMA) break;
      consume(COMMA);
    }
    func.arity = arguments.size();
    consume(RIGHT_PAREN);
    consume(LEFT_BRACE);

    inFunctionDeclaration = false;
    return new Stmt.Function(func, arguments, block(), type);
  }

  /* varDeclaration ::= type identifier ("=" expression)? ";" */
  private Stmt.Var varDeclaration(Token type, Token identifier) throws ParseError {
    Expr.Symbol variable = new Expr.Symbol(-1, identifier, LoxType.get(type.type));
    Expr.Assign equals = null;

    if (match(EQUAL)) {
      Token equal = previous();
      equals = new Expr.Assign(variable, expression(), equal, null);
    }
    consume(SEMICOLON);
    return new Stmt.Var(variable, equals, type);
  }

  /* statement ::= exprStmt | printStmt | block
   *               | if | while | for | break | continue | return | ';' */
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
    if (match(RETURN)) return returnStatement();
    if (match(SEMICOLON)) return null;

    return expressionStatement();
  }

  /* return ::= 'return' expression? ';' */
  private Stmt.Return returnStatement() throws ParseError {
    Token keyword = previous();
    if (match(SEMICOLON)) {
      return new Stmt.Return(null, keyword);
    }
    return new Stmt.Return(expressionStatement().expression, keyword);
  }

  /* block ::= '{' declaration* '}' */
  private Stmt.Block block() throws ParseError {
    inBlock = true;
    Token leftBrace = previous();
    List<Stmt> statements = new ArrayList<>();

    while (!atEnd() && (peek().type != RIGHT_BRACE)) {
      Stmt stmt = declaration();
      if (stmt != null) statements.add(stmt);
    }

    consume(RIGHT_BRACE);
    return new Stmt.Block(statements, leftBrace);
  }

  /* if ::= "if" '(' expression ') statement ( "else" statement )?
   *
   * note that we eagerly find the else, so 'dangling elses'
   * are parsed as if they belonged to the innermost 'if'. */
  private Stmt.If ifStatement() throws ParseError {
    Token ifKeyword = previous();
    Expr condition = condition();
    Stmt then = statement();
    Stmt otherwise = null;
    if (match(ELSE)) {
      otherwise = statement();
    }

    return new Stmt.If(condition, then, otherwise, ifKeyword);
  }

  private Stmt.While whileStatement() throws ParseError {
    Token whileToken = previous();
    Expr condition = condition();
    Stmt body = statement();
    return new Stmt.While(condition, body, whileToken);
  }

  private Stmt forStatement() throws ParseError {
    Stmt declaration = null;
    Expr condition = null;
    Expr after = null;
    Token forToken = previous();

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
      body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(after, after.token)), body.token);
    }
    if (condition == null) condition = new Expr.Literal(true, null, LoxType.BOOL);
    Stmt.While whileLoop = new Stmt.While(condition, body, forToken);

    if (declaration == null) return whileLoop;

    return new Stmt.Block(Arrays.asList(declaration, whileLoop), body.token);
  }

  /* printStmt ::= "print" expression ";" ; */
  private Stmt.Print printStatement() throws ParseError {
    Token print = previous();
    Expr value = expression();
    consume(SEMICOLON);
    return new Stmt.Print(value, print);
  }

  /* exprStmt ::= expression ";" ; */
  private Stmt.Expression expressionStatement() throws ParseError {
    Expr value = expression();
    consume(SEMICOLON);
    return new Stmt.Expression(value, previous());
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

  /* assignment ::= expression (equal expression)?
   * Note that we don't call parseBinary because assignment is right-associative */
  private Expr assignment() throws ParseError {
    Expr lvalue = or();
    if (match(EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL, DOT_EQUAL,
          CARET_EQUAL, PERCENT_EQUAL, AMPERSAND_EQUAL, PIPE_EQUAL)) {
      Token equals = previous();
      Expr rvalue = assignment();
      if (!(lvalue instanceof Expr.Symbol)) {
        error(equals.line, equals.column, "INTERNAL error: pointers not implemented");
        throw new ParseError();
      }
      // enhanced assignment: a &= 2;
      if (equals.type != EQUAL) {
        Token operation = new Token(enhancedAssignment.get(equals.type),
          equals.lexeme.substring(0, 1), equals.line, equals.column, null);
        rvalue = new Expr.Binary(lvalue, rvalue, operation, null);
        equals = new Token(EQUAL, "=", equals.line, equals.column, null);
      }
      return new Expr.Assign((Expr.Symbol)lvalue, rvalue, equals, null);
    }
    return lvalue;
  }

  /* or ::= and ("or" and)* */
  private Expr or() throws ParseError {
    return parseBinary(this::and, LoxType.BOOL, Expr.Logical.class, OR);
  }

  /* and ::= equality ("and" equality)* */
  private Expr and() throws ParseError {
    return parseBinary(this::equality, LoxType.BOOL, Expr.Logical.class, AND);
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
    if (match(BANG, MINUS, PLUS_PLUS, MINUS_MINUS)) {
      Token operator = previous();
      Expr right = prefixUnary();

      // desugar: x++ == (x = x + 1); --x == (x = x - 1)
      if (operator.type == PLUS_PLUS || operator.type == MINUS_MINUS) {
        if (!(right instanceof  Expr.Symbol)) {
          error(operator, "Can only pre-increment or -decrement variables");
          return right;
        }
        return new Expr.Assign((Expr.Symbol)right,
          new Expr.Binary(right,
            new Expr.Literal(1,null, LoxType.INT),
            new Token(operator.type == PLUS_PLUS ? PLUS : MINUS, operator.lexeme, operator.line, operator.column, null),
            null), operator, null);
      }

      return new Expr.Unary(right, operator, null);
    }

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
      Token left = previous();
      if (!(primary instanceof Expr.Symbol)) {
        error(left.line, left.column, "INTERNAL error: "
            + "first class functions and function pointers not implemented");
      } else if (match(RIGHT_PAREN)) {
        // we need to look up the type of this function in the symbol table
        primary = new Expr.Call((Expr.Symbol)primary, new ArrayList<>(), left, null);
      } else {
        primary = new Expr.Call((Expr.Symbol)primary, arguments(), left, null);
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

  /* primary ::= NUMBER | STRING | "false" | "true" | "(" expression ")" ; */
  private Expr primary() throws ParseError {
    if (match(FALSE)) return new Expr.Literal(false, previous(), LoxType.BOOL);
    if (match(TRUE)) return new Expr.Literal(true, previous(), LoxType.BOOL);
    if (match(IDENTIFIER)) return new Expr.Symbol(-1, previous(), null);

    if (match(NUMBER)) {
      return new Expr.Literal(previous().value, previous(),
        previous().value instanceof Integer ? LoxType.INT : LoxType.DOUBLE);
    }

    if (match(STRING)) {
      String value = previous().value.toString();
      if (match(STRING)) {
        value += previous().value;
      }
      return new Expr.Literal(value, previous(), LoxType.STRING);
    }

    // groupings
    if (match(LEFT_PAREN)) {
      Token left = previous();
      // empty parentheses
      if (match(RIGHT_PAREN)) return new Expr.Grouping(null, left, null);
      Expr expr = expression();
      consume(RIGHT_PAREN);
      return new Expr.Grouping(expr, left, null);
    }

    if (peek() == null) {
      error(previous().line, previous().column + previous().lexeme.length(),
          "Unexpected end of file");
    } else {
      error(peek().line, peek().column,
          "Unhandled token " + peek().type);
      panic();
    }
    throw new ParseError();
  }

  /* panic mode; advance until we're sure the expression is over */
  @SuppressWarnings("fallthrough")
  private void panic() {
    while (!atEnd()) {
      switch (peek().type) {
        case RIGHT_BRACE:
          if (!inBlock) break;
          return;
        case INT:
        case DOUBLE:
        case STRING_TYPE:
        case BOOL:
          if (inFunctionDeclaration) break;
        case SEMICOLON:
        case CLASS:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
        case LEFT_BRACE:
          return;
      }

      advance();
    }
  }

  private Expr parseBinary(BinaryExprParser function, LoxType max, Token.Type ... input) throws ParseError {
    return parseBinary(function, max, Expr.Binary.class, input);
  }

  private Expr parseBinary(BinaryExprParser function, LoxType max, Class<? extends Expr> expected,
      Token.Type ... input) throws ParseError {
    Expr result = function.parse();
    while (match(input)) {
      Token operator = previous();
      Expr right = function.parse();
      try {
        result = expected.getDeclaredConstructor(Expr.class, Expr.class, Token.class, LoxType.class)
          .newInstance(result, right, operator, result.type);
      } catch (ReflectiveOperationException e) {
        e.printStackTrace();
        throw new ParseError("Illegal operation: " + e.toString());
      }
    }
    return result;
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
      panic();
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
  private static class ParseError extends Exception {
    ParseError() { super(); }
    ParseError(String s) { super(s); }
  }
}
