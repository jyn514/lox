package lox.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static lox.java.Lox.error;
import static lox.java.Token.Type.*;

class Compiler extends Pass<List<Stmt>, List<String>>
        implements Stmt.Visitor<String>, Expr.Visitor<Compiler.ExprNode> {
  private final List<List<String>> assembly = new ArrayList<>();
  private final Map<String, ExprNode> variables = new HashMap<>();
  private LoopNode currentLoop = null;
  private long currentVariables = 0, currentIntermediates = 0, currentLabel = 0;
  private int context = 1;
  private String currentBlock = "start";

  private static final String PUTS_TRUE = "@.true_str",
    PUTS_FALSE = "@.false_str",
    PUTS_NULL = "@.null_str",
    PRINTF_INT = "@.int_format",
    PRINTF_DOUBLE = "@.double_format",
    CONST_TRUE = "@.true",
    CONST_FALSE = "@.false";

  private static final String LLVM_CONST = "private unnamed_addr constant ",
    PUTS_TRUE_DECLARATION = String.format("%s = %s %s",
        PUTS_TRUE, LLVM_CONST, "[5 x i8] c\"true\\00\""),
    PUTS_FALSE_DECLARATION = String.format("%s = %s %s",
        PUTS_FALSE, LLVM_CONST, "[6 x i8] c\"false\\00\""),
    PUTS_NULL_DECLARATION = String.format("%s = %s %s",
        PUTS_NULL, LLVM_CONST, "[5 x i8] c\"null\\00\""),
    PRINTF_INT_DECLARATION = String.format("%s = %s %s",
        PRINTF_INT, LLVM_CONST, "[4 x i8] c\"%d\\0A\\00\""),
    PRINTF_DOUBLE_DECLARATION = String.format("%s = %s %s",
      PRINTF_DOUBLE, LLVM_CONST, "[4 x i8] c\"%f\\0A\\00\""),
    CONST_TRUE_DECLARATION = String.format("%s = %s i1 1",
        CONST_TRUE, LLVM_CONST),
    CONST_FALSE_DECLARATION = String.format("%s = %s i1 0",
        CONST_FALSE, LLVM_CONST);

  private static final Map<LoxType, String> llvmTypes = Map.ofEntries(
    entry(LoxType.BOOL, "i1"),
    entry(LoxType.INT, "i32"),
    entry(LoxType.DOUBLE, "double"),
    entry(LoxType.STRING, "i8*"),
    entry(LoxType.VOID, "void")
  );

  private static final Map<LoxType, Map<Token.Type, String>> operators = Map.of(
    LoxType.DOUBLE, Map.ofEntries(
      entry(PLUS, "fadd double"),
      entry(MINUS, "fsub double"),
      entry(STAR, "fmul double"),
      entry(SLASH, "fdiv double"),
      entry(PERCENT, "frem double"),
      entry(EQUAL, "store double"),
      entry(EQUAL_EQUAL, "fcmp oeq double"),
      entry(BANG_EQUAL, "fcmp one double"),
      entry(LESS, "fcmp olt double"),
      entry(LESS_EQUAL, "fcmp ole double"),
      entry(GREATER, "fcmp ogt double"),
      entry(GREATER_EQUAL, "fcmp oge double")
    ),
    LoxType.INT, Map.ofEntries(
      entry(PLUS, "add i32"),
      entry(MINUS, "sub i32"),
      entry(STAR, "mul i32"),
      entry(SLASH, "sdiv i32"),
      entry(PERCENT, "srem i32"),
      entry(AMPERSAND, "and i32"),
      entry(PIPE, "or i32"),
      entry(CARET, "xor i32"),
      entry(EQUAL, "store i32"),
      entry(EQUAL_EQUAL, "icmp eq i32"),
      entry(BANG_EQUAL, "icmp ne i32"),
      entry(LESS, "icmp slt i32"),
      entry(LESS_EQUAL, "icmp sle i32"),
      entry(GREATER, "icmp sgt i32"),
      entry(GREATER_EQUAL, "icmp sge i32")
    ),
    // TODO: this is a near-duplicate of the map for INT
    LoxType.BOOL, Map.ofEntries(
      entry(PLUS, "add i1"),
      entry(MINUS, "sub i1"),
      entry(STAR, "mul i1"),
      entry(SLASH, "sdiv i1"),
      entry(PERCENT, "srem i1"),
      entry(AMPERSAND, "and i1"),
      entry(PIPE, "or i1"),
      entry(CARET, "xor i1"),
      entry(EQUAL, "store i1"),
      entry(EQUAL_EQUAL, "icmp eq i1"),
      entry(BANG_EQUAL, "icmp ne i1"),
      entry(LESS, "icmp slt i1"),
      entry(LESS_EQUAL, "icmp sle i1"),
      entry(GREATER, "icmp sgt i1"),
      entry(GREATER_EQUAL, "icmp sge i1")
    )
  );

  Compiler(List<Stmt> program) {
    super(program);
  }

  public List<String> runPass() {
    assembly.clear();
    add("declare i32 @puts(i8* nocapture) nounwind", 0);
    add("declare i32 @printf(i8* nocapture, ...) nounwind", 0);
    add(PUTS_TRUE_DECLARATION, 0);
    add(PUTS_FALSE_DECLARATION, 0);
    add(PUTS_NULL_DECLARATION, 0);
    add(PRINTF_INT_DECLARATION, 0);
    add(PRINTF_DOUBLE_DECLARATION, 0);
    add(CONST_TRUE_DECLARATION, 0);
    add(CONST_FALSE_DECLARATION, 0);

    add("\ndefine i32 @main() {");
    add(currentBlock + ':');

    for (Stmt stmt : input) {
      add(stmt.accept(this));
    }

    add("ret i32 0");
    add("}");

    return flatten(assembly);
  }

  /*
     private boolean lvalue(Expr x) {
     if (x instanceof Expr.Symbol) return true;
     return (x instanceof Access) && lvalue(x.variable);
     }
     */

  public String visitStmt(Stmt.While loop) {
    LoopNode oldLoop = currentLoop;
    currentLoop = new LoopNode("LoopStartLabel" + currentLabel++,
        "LoopEndLabel" + currentLabel++);
    String afterLabel = "LoopAfterConditionLabel" + currentLabel++;

    /* before either condition or body */
    add(currentLoop.startLabel + ':');

    ExprNode cond = loop.condition.accept(this);
    /* after we calculate condition, before body */
    add("br i1 " + cond.register + ", label %" + afterLabel
        + ", label %" + currentLoop.endLabel);
    /* LLVM requires an explicit ELSE branch, we just go immediately after */
    add(afterLabel + ':');

    /* main loop */
    add(loop.body.accept(this));
    /* unconditionally go back to start, we calculate condition there */
    add("br label %" + currentLoop.startLabel);

    currentLoop = oldLoop;
    return currentLoop.endLabel + ':';
  }

  public String visitStmt(Stmt.If branch) {
    ExprNode branchResult = branch.condition.accept(this);
    String thenLabel = "ThenLabel" + currentLabel++,
           elseLabel = "ElseLabel" + currentLabel++,
           afterLabel = "AfterLabel" + currentLabel++;

    add("br i1 " + branchResult.register + ", label %" + thenLabel
        + ", label %" + (branch.otherwise == null ? afterLabel : elseLabel));

    add(thenLabel + ':');
    add(branch.then.accept(this));
    add("br label %" + afterLabel);

    if (branch.otherwise != null) {
      add(elseLabel + ':');
      add(branch.otherwise.accept(this));
      add("br label %" + afterLabel);
    }

    return afterLabel + ':';
  }

  /* TODO: print should be a function primitive, not a statement */
  public String visitStmt(Stmt.Print print) {
    String call = String.format("%%unused_register%d = call i32 ",
        currentIntermediates++);
    // need to evaluate expression even if null (function calls could have side effects)
    final ExprNode expr = print.expression.accept(this), printNode = new ExprNode(LoxType.STRING);

    switch(print.expression.type) {
      case STRING:
        call += "@puts (i8* " + expr.register + ')';
        break;
      case DOUBLE:
        add(assign(printNode, loadStringPointer(PRINTF_DOUBLE, 4)));
        call += "(i8*, ...) @printf (" + printNode +  ", " + expr + ')';
        break;
      case INT:
        add(assign(printNode, loadStringPointer(PRINTF_INT, 4)));
        call += "(i8*, ...) @printf (" + printNode + ", " + expr + ')';
        break;
      case BOOL:
        ExprNode printTrue = new ExprNode(LoxType.STRING),
                 printFalse = new ExprNode(LoxType.STRING);
        add(assign(printTrue, loadStringPointer(PUTS_TRUE, 5)));
        add(assign(printFalse, loadStringPointer(PUTS_FALSE, 6)));

        ExprNode isTrue = new ExprNode(getTmp(), llvmTypes.get(LoxType.BOOL));
        add(assign(isTrue, "icmp eq " + expr + ", 1"));
        add(assign(printNode, "select " + isTrue + ", "
                    + printTrue + ", " + printFalse));

        call += "@puts (" + printNode + ')';
        break;
      default:
        error(print.expression.token.line, print.expression.token.column, "Unknown type to print");
    }
    return call;
  }

  public String visitStmt(Stmt.Expression expr) {
    // we need to visit expr in case of an assignment
    expr.expression.accept(this);
    return "";
  }

  public String visitStmt(Stmt.Block block) {
    for (Stmt stmt : block.statements) {
      add(stmt.accept(this));
    }
    return "";
  }

  public String visitStmt(Stmt.Var var) {
    // scoping/mangling is handled by Annotate pass
    ExprNode register = new ExprNode("%" + var.identifier.token.lexeme,
        llvmTypes.get(var.identifier.type) + '*');
    variables.put(var.identifier.token.lexeme, register);
    // store variable on the stack
    add(assign(register, "alloca " +
          register.llvmType.substring(0, register.llvmType.length() - 1)));
    if (var.equals != null) var.equals.accept(this);
    return "";
  }

  public String visitStmt(Stmt.Function func) {
    StringBuilder asm = new StringBuilder();
    asm.append("define ").append(llvmTypes.get(func.identifier.type))
       .append(" @").append(func.identifier.token.lexeme).append('(');

    for (Expr.Symbol id : func.arguments) {
      asm.append(llvmTypes.get(id.type)).append(' ')
         .append(id.token.lexeme).append(',');
    }
    // end of arguments, replace "," with ")"
    if (func.arguments.size() > 0)
      asm.setCharAt(asm.length() - 1, ')');
    else asm.append(')');

    // functions need to be top level
    context++;

    add(asm.append(" {").toString());
    add((currentBlock = "funcStart" + currentLabel++) + ':');
    add(func.body.accept(this));
    add("}");

    context--;
    return "";
  }

  @Override
  public String visitStmt(Stmt.Return stmt) {
    if (stmt.value == null) {
      return "ret void";
    }
    return "ret " + stmt.value.accept(this);
  }

  public ExprNode visitExpr(Expr.Symbol symbol) {
    ExprNode var = variables.get(symbol.token.lexeme),
             value = new ExprNode(var.register + "_tmp" + currentVariables++,
                var.llvmType.substring(0, var.llvmType.length() - 1));
    add(assign(value, "load " + value.llvmType + ", " + var));
    return value;
  }

  public ExprNode visitExpr(Expr.Grouping expr) {
    return expr.expression.accept(this);
  }

  public ExprNode visitExpr(Expr.Logical expr) {
    /*
     *  if (expr.type == OR ? left : !left) return left
     *  else return right;
     */
    String rightLabel = "logicRightLabel" + currentLabel++,
           endLabel = "logicEndLabel" + currentLabel++;

    ExprNode result = new ExprNode(expr.type),
             left = expr.left.accept(this),
             cond;

    if (expr.token.type == Token.Type.OR) {
      cond = left;
    } else if (expr.token.type == Token.Type.AND) {
      cond = new ExprNode(expr.type);
      // cond = !left
      // note that we can't make the condition part of the br
      // because br only allows registers or constant expressions
      // http://llvm.org/docs/LangRef.html#constantexprs
      add(assign(cond, "icmp eq i1 0, " + left.register));
    } else {
      // this is going to bite us when we add ternary operator
      throw new IllegalArgumentException("INTERNAL error: "
          + "only AND or OR are allowed as conditional expressions");
    }

    /* if (cond) goto end; else { eval right; goto end; } */
    add("br " + cond + ", label %" + endLabel + ", label %" + rightLabel);
    add(rightLabel + ':');
    String originalLabel = currentBlock;
    currentBlock = rightLabel;

    ExprNode right = expr.right.accept(this);
    add("br label %" + endLabel);

    add(endLabel + ':');
    add(assign(result, String.format("phi i1 [ %s, %%%s ], [ %s, %%%s ]",
            left.register, originalLabel, right.register, currentBlock)));
    currentBlock = endLabel;

    return result;
  }

  public ExprNode visitExpr(Expr.Unary unary) {
    ExprNode original = unary.right.accept(this), result = new ExprNode(unary.right.type);
    if (unary.token.type == Token.Type.MINUS) {
      add(assign(result, operators.get(unary.type).get(Token.Type.MINUS)
        + " 0" + (unary.right.type == LoxType.DOUBLE ? ".0, " : ", ") + original.register));
    } else add(assign(result, not(original)));
    return result;
  }

  private String not(ExprNode node) {
    return "icmp eq i1 0, " + node.register;
  }

  public ExprNode visitExpr(Expr.Call call) {
    ExprNode result = new ExprNode(call.type);
    StringBuilder builder = new StringBuilder();

    if (call.callee.type != LoxType.VOID) {
      builder.append(result.register).append(" = ");
    }
    builder.append("call ").append(result.llvmType)
           .append(" @").append(call.callee.token.lexeme).append('(');

    for (Expr expr : call.arguments) {
      ExprNode arg = expr.accept(this);
      builder.append(arg.llvmType).append(' ').append(arg.register).append(',');
    }

    if (call.arguments.size() > 0) {
      builder.setCharAt(builder.length() - 1, ')');
      add(assign(result, builder.toString()));
    } else {
      builder.append(')');
      add(builder.toString());
    }

    return result;
  }

  public ExprNode visitExpr(Expr.Assign assign) {
    ExprNode value = assign.rvalue.accept(this),
             lvalue = variables.get(assign.lvalue.token.lexeme);
    // copy: assign.lvalue = 0 + value
    add("store " + value + ", " + lvalue);
    return value;
  }

  public String visitStmt(Stmt.LoopControl keyword) {
    if (currentLoop == null) {
      error(keyword.token.line, keyword.token.column,
          "Illegal keyword '" + keyword.token.lexeme + "' when not inside a loop");
      return "";
    } else {
      return "br label %" + (keyword.token.type == BREAK ?
          currentLoop.endLabel : currentLoop.startLabel);
    }
  }

  public ExprNode visitExpr(Expr.Binary expr) {
    // TODO
    assert expr.left.type == expr.right.type && expr.right.type == expr.type;

    ExprNode left = expr.left.accept(this), right = expr.right.accept(this);

    // llvm assembly instruction
    String operation = operators.get(expr.type).get(expr.token.type);
    if (operation == null) {
      error(expr.token.line, expr.token.column,
          "Illegal operator '" + expr.token.lexeme + "' for type " + expr.type);
    }
    ExprNode result = new ExprNode(expr.type);

    add(assign(result, operation + ' ' + left.register + ", " + right.register));
    return result;
  }

  public ExprNode visitExpr(Expr.Literal expr) {
    if (expr.type == null) {
      error(expr.token.line, expr.token.column,"INTERNAL error: could not resolve type of literal expression " + expr);
      return null;
    }

    if (expr.type == LoxType.BOOL) {
      ExprNode bool = new ExprNode(LoxType.BOOL);
      add(assign(bool, "load i1, i1* "
            + ((boolean)expr.value ? CONST_TRUE : CONST_FALSE)));
      return bool;
    }

    // note: adding global constant
    ExprNode constant = new ExprNode("@constant" + currentVariables++,
        llvmTypes.get(expr.type));

    if (expr.type == LoxType.INT || expr.type == LoxType.DOUBLE) {
      add(String.format("%s = constant %s %s",
            constant.register, constant.llvmType, expr.value.toString()), 0);
      ExprNode literal = new ExprNode(expr.type);
      add(assign(literal, String.format("load %s, %s* %s",
              literal.llvmType, literal.llvmType, constant.register)));
      return literal;
    }

    ExprNode result = new ExprNode(expr.type);

    if (expr.type == LoxType.STRING) {
      String type = String.format("[%d x i8]", expr.value.toString().length() + 1);

      add(String.format("%s = %s %s c\"%s\\00\"",
            constant.register, LLVM_CONST, type, expr.value), 0);
      add(assign(result, String.format("getelementptr %s, %s* %s, i32 0, i64 0",
              type, type, constant.register)));
      return result;
    } else if (expr.type == LoxType.VOID) {
      // e.g. print f(); where f is a void function
      add(assign(result, String.format("getelementptr [5 x i8], [5 x i8]* %s, i32 0, i64 0",
              PUTS_NULL)));
      return result;
    }

    throw new IllegalArgumentException("Unknown literal type " + expr.type);
  }

  private String loadStringPointer(String register, int length) {
    String type = String.format("[%d x i8]", length);
    return String.format("getelementptr %s, %s* %s, i32 0, i64 0",
            type, type, register);
  }

  private String getTmp() {
    return "%tmp" + currentIntermediates++;
  }

  private String assign(ExprNode node, String s) {
    return node.register + " = " + s;
  }

  // default to current function context
  private void add(String s) {
    add(s, context);
  }

  private void add(String s, int context) {
    // allow all functions to be at top level
    // allow global contexts
    // allow default to be inside main function
    while (assembly.size() <= context) assembly.add(new ArrayList<>());
    assembly.get(context).add(s);
  }

  /*
   * 2D -> 1D; in python would be [item for sublist in array for item in sublist]
   * see https://stackoverflow.com/q/8559092
   * as well as https://stackoverflow.com/q/952914
   */
  private static <T> List<T> flatten(List<List<T>> list) {
    List<T> result = new ArrayList<>();
    for (List<T> sublist : list) {
      result.addAll(sublist);
    }
    return result;
  }

  class ExprNode {
    final String register;
    final String llvmType;

    ExprNode(LoxType type) {
      this(getTmp(), llvmTypes.get(type));
    }

    ExprNode(String register, String type) {
      this.register = register;
      llvmType = type;
    }

    public String toString() {
      return llvmType + ' ' + register;
    }
  }

  class LoopNode {
    final String startLabel, endLabel;

    LoopNode(String start, String end) {
      this.startLabel = start;
      this.endLabel = end;
    }
  }
}

