package lox.java;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import static lox.java.Lox.error;

class Annotate extends Pass<List<Stmt>, List<Stmt>>
  implements Stmt.Visitor<Void>, Expr.Visitor<Void> {
  private static final Random rand = new Random();
  private Scope<String> scope = new Scope<>();
  private final Map<String, Expr.Symbol> types = new HashMap<>();

  // boilerplate start
  Annotate(List<Stmt> input) {
    super(input);
  }

  public List<Stmt> runPass() {
    for (Stmt stmt : input) {
      stmt.accept(this);
    }
    return input;
  }

  public Void visitStmt(Stmt.Expression stmt) { return stmt.expression.accept(this); }
  public Void visitStmt(Stmt.Print stmt) { return stmt.expression.accept(this); }
  public Void visitStmt(Stmt.If stmt) {
    stmt.condition.accept(this);
    stmt.then.accept(this);
    return stmt.otherwise == null ? null : stmt.otherwise.accept(this);
  }
  public Void visitStmt(Stmt.While stmt) {
    stmt.condition.accept(this);
    return stmt.body.accept(this);
  }
  public Void visitStmt(Stmt.LoopControl keyword) { return null; }

  // boilerplate end

  public Void visitStmt(Stmt.Var var) {
    create(var.identifier);
    return null;
  }

  public Void visitStmt(Stmt.Block block) {
    Scope<String> oldScope = scope;
    scope = new Scope<>(scope);
    for (Stmt stmt : block.statements) {
      stmt.accept(this);
    }
    scope = oldScope;
    return null;
  }

  public Void visitStmt(Stmt.Function func) {
    func.identifier.arity = func.arguments.size();
    create(func.identifier);
    func.body.accept(this);
    return null;
  }

  public Void visitExpr(Expr.Unary expr) { return expr.right.accept(this); }
  public Void visitExpr(Expr.Binary expr) {
    expr.left.accept(this);
    return expr.right.accept(this);
  }
  public Void visitExpr(Expr.Logical expr) {
    expr.left.accept(this);
    return expr.right.accept(this);
  }
  public Void visitExpr(Expr.Grouping expr) { return expr.expression.accept(this); }
  public Void visitExpr(Expr.Literal expr) { return null; }

  public Void visitExpr(Expr.Symbol symbol) {
    String oldName = symbol.name.lexeme;
    /* ideally we would replace 'symbol' outright,
     * but we don't have a proper reference */
    symbol.name.lexeme = scope.get(oldName);
    Expr.Symbol shouldBe = types.get(symbol.name.lexeme);
    if (shouldBe == null) {
      error(symbol.name.line, symbol.name.column,
          "Undeclared variable " + oldName);
    } else {
      symbol.type = shouldBe.type;
      symbol.arity = shouldBe.arity;
    }
    return null;
  }

  public Void visitExpr(Expr.Assign expr) {
    Expr.Symbol old = expr.lvalue;
    expr.lvalue = retrieve(old);
    if (expr.lvalue == null) error(old.name.line, old.name.column,
        "Undeclared variable " + old.name.lexeme);
    expr.rvalue.accept(this);
    return null;
  }

  public Void visitExpr(Expr.Call call) {
    String oldName = call.callee.name.lexeme;
    call.callee = retrieve(call.callee);

    if (call.callee == null) {
      error(call.paren.line, call.paren.column, "Undeclared function " + oldName);
    } else if (call.arguments.size() != call.callee.arity) {
      error(call.paren.line, call.paren.column,
          "Invalid number of arguments to function '" + oldName
          + "' (expected " + call.callee.arity
          + ", got " + call.arguments.size()
          + ')');
    } else {
      call.type = call.callee.type;
    }

    return null;
  }

  private void create(Expr.Symbol symbol) {
    if (scope.getImmediate(symbol.name.lexeme) != null) {
      error(symbol.name.line, symbol.name.column,
          "Illegal redeclaration of variable " + symbol.name.lexeme);
      return;
    }
    String mangled = mangle(symbol.name.lexeme);
    scope.put(symbol.name.lexeme, mangled);
    symbol.name.lexeme = mangled;

    // TODO
    assert symbol.type != null && symbol.type != LoxType.UNDEFINED
      && symbol.type != LoxType.ERROR;
    types.put(mangled, symbol);
  }

  private Expr.Symbol retrieve(Expr.Symbol symbol) {
    return types.get(scope.get(symbol.name.lexeme));
  }

  private String mangle(String name) {
    name += '_';
    while (types.containsKey(name)) {
      name += genChar();
    }
    return name;
  }

  private char genChar() {
    return (char)(rand.nextInt(26) + 'a');
  }
}
