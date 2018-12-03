package lox.java;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static lox.java.Token.Type.*;
import static lox.java.Lox.error;

/*
 * Recursive descent top-down AST parser.
 * Methods called sooner have lower precedence than later methods.
 */
public class Parser {
    private final List<Token> tokens;
    private final List<Stmt> result = new ArrayList<>();
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /* program ::= varDeclaration* */
    public List<Stmt> parse() {
        // cache result if we've already parsed the tokens
        if (current != 0) return result;

        while (!atEnd()) {
            try {
                result.add(declaration());
            } catch (ParseError e) {}
        }
        return result;
    }

    /* STATEMENTS */

    /* declaration = funDeclaration | varDeclaration | statement */
    private Stmt declaration() {
        if (match(INT, BOOL, CHAR, DOUBLE)) {
            Token id = consume(IDENTIFIER);
            if (match(LEFT_PAREN)) return funDeclaration(id);
            return varDeclaration(id);
        }
        return statement();
    }

    /* funDeclaration :: type identifier "(" ( type identifier "," )* ") block */
    private Stmt.Function funDeclaration(Token identifier) {
        Expr.Symbol func = new Expr.Symbol(identifier, previous());
        List<Expr.Symbol> arguments = new ArrayList<>();
        while (match(INT, BOOL, CHAR, DOUBLE)) {
            Token type = previous();
            arguments.add(new Expr.Symbol(consume(IDENTIFIER), type));
        }
        consume(RIGHT_PAREN);
        return new Stmt.Function(func, arguments, block());
    }

    /* varDeclaration ::= type identifier ("=" expression)? ";" */
    private Stmt.Var varDeclaration(Token identifier) {
        Expr.Symbol variable = new Expr.Symbol(identifier, previous());
        Expr value = null;
        if (match(EQUAL)) {
            value = expression();
        }
        consume(SEMICOLON);
        return new Stmt.Var(variable, value);
    }

    /* statement ::= exprStmt | printStmt | block
     *               | if | while | for | break | continue; */
    private Stmt statement() {
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

        return expressionStatement();
    }

    /* block ::= '{' declaration* '}' */
    private Stmt.Block block() {
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
    private Stmt.If ifStatement() {
        Expr condition = condition();
        Stmt then = statement();
        Stmt otherwise = null;
        if (match(ELSE)) {
            otherwise = statement();
        }

        return new Stmt.If(condition, then, otherwise);
    }

    private Stmt.While whileStatement() {
        Expr condition = condition();
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() {
        Stmt declaration = null;
        Expr condition = null;
        Expr after = null;

        consume(LEFT_PAREN);
        if (!match(SEMICOLON)) {
            if (match(INT, DOUBLE, BOOL, CHAR)) declaration = declaration();
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
        if (condition == null) condition = new Expr.Literal(true);
        Stmt.While whileLoop = new Stmt.While(condition, body);

        if (declaration == null) return whileLoop;

        return new Stmt.Block(Arrays.asList(declaration, whileLoop));
    }

    /* printStmt ::= "print" expression ";" ; */
    private Stmt.Print printStatement() {
        Expr value = expression();
        consume(SEMICOLON);
        return new Stmt.Print(value);
    }

    /* exprStmt ::= expression ";" ; */
    private Stmt.Expression expressionStatement() {
        Expr value = expression();
        consume(SEMICOLON);
        return new Stmt.Expression(value);
    }


    /* EXPRESSIONS */

    /* only used by statements */
    private Expr condition() {
        consume(LEFT_PAREN);
        Expr condition = expression();
        consume(RIGHT_PAREN);
        return condition;
    }

    /*
     * expression ::= comma
     */
    private Expr expression() {
        return comma();
    }

    /* comma ::= expression ',' expression */
    private Expr comma() {
        return parseBinary(() -> assignment(), COMMA);
    }

    /* assignment ::= expression ('=' expression)?
     * Note that we don't call parseBinary because assignment is right-associative */
    private Expr assignment() {
        Expr lvalue = or();
        if (match(EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL, DOT_EQUAL,
                  CARET_EQUAL, PERCENT_EQUAL, AMPERSAND_EQUAL, PIPE_EQUAL)) {
            Token equals = previous();
            Expr rvalue = assignment();
            return new Expr.Assign(lvalue, equals, rvalue);
        }
        return lvalue;
    }

    /* or ::= and ("or" and)* */
    private Expr or() {
        Expr left = and();
        while (match(OR)) {
            left = new Expr.Logical(left, previous(), and());
        }
        return left;
    }

    /* and ::= equality ("and" equality)* */
    private Expr and() {
        Expr left = equality();
        while (match(AND)) {
            left = new Expr.Logical(left, previous(), equality());
        }
        return left;
    }

    /*
     * equality ::= comparison ( ( "!=" | "==" ) comparison )*
     */
    private Expr equality() {
        return parseBinary(() -> comparison(), BANG_EQUAL, EQUAL_EQUAL);
    }

    /* comparison ::= bitwise ( ( ">" | ">=" | "<" | "<=" ) bitwise )* ; */
    private Expr comparison() {
        return parseBinary(() -> bitwise(), GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    /* bitwise ::= addition ( ( "^" | "|" | "&" ) addition )* ; */
    private Expr bitwise() {
        return parseBinary(() -> addition(), CARET, PIPE, AMPERSAND);
    }

    /* addition ::= multiplication ( ( "-" | "+" ) multiplication )* ; */
    private Expr addition() {
        return parseBinary(() -> multiplication(), MINUS, PLUS);
    }

    /* multiplication ::= unary ( ( "/" | "*" | "%" ) unary )* ; */
    private Expr multiplication() {
        return parseBinary(() -> prefixUnary(), SLASH, STAR, PERCENT);
    }

    /* prefixUnary ::= ( "!" | "-" | "++" | "--" ) prefixUnary | postfixUnary ; */
    private Expr prefixUnary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            return new Expr.Unary(operator, prefixUnary());
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
    private Expr postfixUnary() {
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
    private Expr call() {
        Expr primary = primary();
        while (match(LEFT_PAREN)) {
            if (match(RIGHT_PAREN)) {
                primary = new Expr.Call(primary, previous(), null);
            } else {
                primary = new Expr.Call(primary, previous(), arguments());
                consume(RIGHT_PAREN);
            }
        }
        return primary;
    }

    /* arguments ::= expression ( "," expression )* */
    private List<Expr> arguments() {
        List<Expr> result = new ArrayList<>();
        result.add(expression());

        while(match(COMMA)) {
            result.add(expression());
        }

        return result;
    }

    /* primary ::= NUMBER | STRING | "false" | "true" | "null" | "(" expression ")" ; */
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NULL)) return new Expr.Literal(null);
        if (match(IDENTIFIER)) return new Expr.Symbol(previous(), null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().value);
        }

        // groupings
        if (match(LEFT_PAREN)) {
            // empty parentheses
            if (match(RIGHT_PAREN)) return new Expr.Grouping(null);
            Expr expr = expression();
            consume(RIGHT_PAREN);
            return new Expr.Grouping(expr);
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

    private Expr parseBinary(BinaryExprParser function, Token.Type ... types) {
        Expr result = function.parse();
        while (match(types)) {
            result = new Expr.Binary(result, previous(), function.parse());
        }
        return result;
    }

    /*
     * Meant to be used as an anonymous class with lambdas.
     */
    private interface BinaryExprParser {
        Expr parse();
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
        return atEnd() ? null : tokens.get(current);
    }

    private Token advance() {
        return tokens.get(current++);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private boolean atEnd() {
        return current >= tokens.size();
    }

    @SuppressWarnings("serial")
    private static class ParseError extends RuntimeException {}
}
