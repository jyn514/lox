package lox.java;

import lox.java.Token;

public class ASTPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {

    // tmp
    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
            new Expr.Unary(
                new Token(Token.Type.MINUS, "-", 1, 1),
                new Expr.Literal(123)),
                new Token(Token.Type.STAR, "*", 1, 1),
                new Expr.Grouping(
                new Expr.Literal(45.67)));

        System.out.println(new ASTPrinter().print(expression));
    }

    public String print(Expr expr) {
        return expr.accept(this);
    }

    public String print(Stmt stmt) {
        return stmt.accept(this);
    }

    public String visitStmt(Stmt.Expression stmt) {
        return parenthesize("exprStatement", stmt.expression);
    }

    public String visitStmt(Stmt.If stmt) {
        return parenthesize("if", stmt.condition, stmt.then, stmt.otherwise);
    }

    public String visitStmt(Stmt.Function stmt) {
        return parenthesize("function", stmt.identifier, stmt.arguments, stmt.body);
    }

    public String visitStmt(Stmt.Block stmt) {
        return parenthesize("block", stmt.statements.toArray());
    }

    public String visitStmt(Stmt.While stmt) {
        return parenthesize("while", stmt.condition, stmt.body);
    }

    public String visitStmt(Stmt.LoopControl stmt) {
        return parenthesize(stmt.keyword.lexeme);
    }

    public String visitStmt(Stmt.Var stmt) {
        return parenthesize("var", stmt.identifier, stmt.expression);
    }

    public String visitStmt(Stmt.Print stmt) {
        return parenthesize("print", stmt.expression);
    }

    public String visitExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    public String visitExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    public String visitExpr(Expr.Literal expr) {
        return expr.value == null ? "null" : expr.value.toString();
    }

    public String visitExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    public String visitExpr(Expr.Assign expr) {
        return parenthesize(expr.equal.lexeme, expr.lvalue, expr.rvalue);
    }

    public String visitExpr(Expr.Call expr) {
        return parenthesize("call", expr.arguments.toArray());
    }

    public String visitExpr(Expr.Logical expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    public String visitExpr(Expr.Symbol expr) {
        return "(lookup " + expr.name.lexeme + ')';
    }

    private String parenthesize(String name, Object ... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append('(').append(name);
        for (Object expr : exprs) {
            builder.append(' ');
            if (expr == null) builder.append("null");
            else if (expr instanceof Stmt)
                builder.append(((Stmt)expr).accept(this));
            else if (expr instanceof Expr)
                builder.append(((Expr)expr).accept(this));
        }

        return builder.append(')').toString();
    }
}
