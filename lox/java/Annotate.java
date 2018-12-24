package lox.java;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import static lox.java.Lox.error;
import static lox.java.LoxType.*;

class Annotate extends Pass<List<Stmt>, List<Stmt>>
  implements Stmt.Visitor<Void>, Expr.Visitor<Void> {
  private static final Random rand = new Random();
  private final Map<String, Expr.Symbol> types = new HashMap<>();

  private Scope<String> scope = new Scope<>();
  private Stmt.Function currentFunction = null;
  private boolean returnFound = false;

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
    if (stmt.condition.type != BOOL) {
      error(stmt.condition.token.line, stmt.condition.token.column,
        "Condition for 'if' statement must be boolean; got " + stmt.condition.type);
    }
    stmt.then.accept(this);
    return stmt.otherwise == null ? null : stmt.otherwise.accept(this);
  }

  public Void visitStmt(Stmt.While stmt) {
    stmt.condition.accept(this);
    if (stmt.condition.type != BOOL) {
      error(stmt.condition.token.line, stmt.condition.token.column,
        "Illegal expression type for while condition: expected BOOL, got " + stmt.condition.type);
    }
    return stmt.body.accept(this);
  }

  public Void visitStmt(Stmt.LoopControl keyword) { return null; }

  // boilerplate end

  public Void visitStmt(Stmt.Var var) {
    String originalName = var.identifier.token.lexeme;
    create(var.identifier);
    if (var.equals != null) {
      // TODO: this is such a hack
      String mangled = var.identifier.token.lexeme;
      var.identifier.token.lexeme = originalName;
      var.equals.accept(this);
      var.identifier.token.lexeme = mangled;
    }
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
    String oldName = func.identifier.token.lexeme;
    create(func.identifier);

    Stmt.Function oldFunc = currentFunction;
    boolean oldReturn = returnFound;
    // don't go through Stmt.Block at all
    Scope<String> oldScope = scope;
    scope = new Scope<>(scope);
    // define the arguments in the *current* scope (so you can't define a variable with the same name)
    for (Expr.Symbol symbol : func.arguments) {
      create(symbol);
    }

    // push these on the stack
    currentFunction = func;
    returnFound = false;

    for (Stmt stmt : func.body.statements) {
      stmt.accept(this);
    }

    if (!returnFound) {
      // automatically return void if user didn't put a return statement
      if (func.identifier.type == VOID) {
        func.body.statements.add(new Stmt.Return(null, new Token(
          Token.Type.RETURN, "return", -1, -1, null)));
      } else {
        error(func.identifier.token.line, func.identifier.token.column,
          String.format("Must return a value for function '%s' declared as " + func.identifier.type,
            oldName));
      }
    }

    // pop them off
    currentFunction = oldFunc;
    returnFound = oldReturn;
    scope = oldScope;

    return null;
  }

  @Override
  public Void visitStmt(Stmt.Return stmt) {
    if (currentFunction == null) {
      error(stmt.token.line, stmt.token.column, "Cannot return unless inside function");
      return null;
    } else if (returnFound) {
      // TODO: be smart and allow if (x) return true else return false;
      error(stmt.token.line, stmt.token.column, "Cannot return more than once inside a function");
      return null;
    }
    returnFound = true;
    LoxType actual;
    if (stmt.value != null) {
      stmt.value.accept(this);
      actual = stmt.value.type;
    } else {
      actual = VOID;
    }
    if (actual != currentFunction.identifier.type) {
      error(stmt.token.line, stmt.token.column, "Illegal return type: function declared with type "
        + currentFunction.identifier.type + ", got " + actual);
    }
    return null;
  }

  public Void visitExpr(Expr.Unary expr) {
    expr.right.accept(this);
    if (expr.token.type == Token.Type.BANG) {
      if (expr.right.type != BOOL) {
        error(expr.token.line, expr.token.column, "Expected boolean expression");
      } else {
        expr.type = BOOL;
      }
    } else if (expr.token.type == Token.Type.MINUS) {
      try {
        if (expr.right.type == BOOL) throw new TypeError();
        assertPromotable(expr.right.type, DOUBLE, null);
        expr.type = expr.right.type;
      } catch (TypeError e) {
        error(expr.token.line, expr.token.column, "Expected numeric type, got " + expr.right.type);
      }
    }
    return null;
  }

  public Void visitExpr(Expr.Binary expr) {
    expr.left.accept(this);
    expr.right.accept(this);
    if (expr.token.type == Token.Type.EQUAL_EQUAL) {
      // TODO: if different types, replace by constant false
      /*
      if (expr.left.type != expr.right.type) {
      }
      */
      expr.type = BOOL;
    } else {
      try {
        // TODO: catch max and min types
        expr.type = assertPromotable(expr.left.type, expr.right.type, null);
      } catch (TypeError e) {
        error(expr.token.line, expr.token.column,
          String.format("Illegal operator %s for types %s and %s", expr.token, expr.left.type, expr.right.type));
      }
    }
    return null;
  }

  public Void visitExpr(Expr.Logical expr) {
    expr.left.accept(this);
    expr.right.accept(this);
    if (expr.left.type != BOOL || expr.right.type != BOOL) {
      error(expr.token, String.format("Expected boolean expressions, got %s and %s",
        expr.left.type, expr.right.type));
    }
    expr.type = BOOL;
    return null;
  }

  public Void visitExpr(Expr.Grouping expr) {
    expr.expression.accept(this);
    expr.type = expr.expression.type;
    return null;
  }

  public Void visitExpr(Expr.Literal expr) { return null; }

  public Void visitExpr(Expr.Symbol symbol) {
    String oldName = symbol.token.lexeme;
    /* ideally we would replace 'symbol' outright,
     * but we don't have a proper reference */
    symbol.token.lexeme = scope.get(oldName);
    Expr.Symbol shouldBe = types.get(symbol.token.lexeme);
    if (shouldBe == null) {
      error(symbol.token.line, symbol.token.column,
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
    expr.rvalue.accept(this);
    if (expr.lvalue == null) error(old.token.line, old.token.column,
        "Undeclared variable " + old.token.lexeme);
    else {
      try {
        expr.type = assertPromotable(expr.lvalue.type, expr.rvalue.type, null);
      } catch (TypeError e) {
        error(expr.token.line, expr.token.column,
        "Cannot assign expression of type " + expr.rvalue.type + " to variable of type " + expr.lvalue.type);
      }
    }
    return null;
  }

  public Void visitExpr(Expr.Call call) {
    String oldName = call.callee.token.lexeme;
    call.callee = retrieve(call.callee);

    if (call.callee == null) {
      error(call.token.line, call.token.column, "Undeclared function " + oldName);
    } else if (call.arguments.size() != call.callee.arity) {
      error(call.token.line, call.token.column,
          "Invalid number of arguments to function '" + oldName
          + "' (expected " + call.callee.arity
          + ", got " + call.arguments.size()
          + ')');
    } else {
      call.type = call.callee.type;
    }

    for (Expr arg : call.arguments) {
      arg.accept(this);
    }

    return null;
  }

  private void create(Expr.Symbol symbol) {
    if (scope.getImmediate(symbol.token.lexeme) != null) {
      error(symbol.token.line, symbol.token.column,
          "Illegal redeclaration of variable " + symbol.token.lexeme);
      return;
    }
    String mangled = mangle(symbol.token.lexeme);
    scope.put(symbol.token.lexeme, mangled);
    symbol.token.lexeme = mangled;

    // TODO
    assert symbol.type != null;
    types.put(mangled, symbol);
  }

  private Expr.Symbol retrieve(Expr.Symbol symbol) {
    return types.get(scope.get(symbol.token.lexeme));
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
