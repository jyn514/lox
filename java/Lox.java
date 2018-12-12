package lox.java;

import java.io.IOException;
import java.io.EOFException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.util.List;

import org.gnu.readline.Readline;
import org.gnu.readline.ReadlineLibrary;

public class Lox {
  private static int errors = 0;

  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: " + args[0] + " [file]");
      System.exit(1);
    }
    if (args.length == 1) {
      runFile(args[0]);
    } else if (System.console() == null) {
      runFile("/dev/stdin");
    } else {
      runPrompt();
    }
  }

  public static void run(String input) {
    // new Interpreter(new Parser(new Lexer(input).scanTokens()).parse());
    ASTPrinter printer = new ASTPrinter();
    for (Stmt stmt : new Parser(new Lexer(input).scanTokens()).parse()) {
      System.out.println(printer.print(stmt));
    }
  }

  public static void error(int line, int column, String message) {
    errors++;
    System.err.println(("" + line) + ':' + column
        + ": error: " + message);
  }

  private static void runFile(String path) throws IOException {
    try {
      run(new String(Files.readAllBytes(Paths.get(path))));
    } catch (NoSuchFileException e) {
      System.err.println("File not found: " + path);
    }
    if (errors > 0) {
      System.err.print("" + errors + " error");
      if (errors > 1) System.err.println('s');
      else System.err.println();
      System.exit(2);
    }
  }

  private static void runPrompt() throws IOException {
    Readline.load(ReadlineLibrary.GnuReadline);
    Readline.initReadline("Lox");
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        Readline.cleanup();
      }
    });
    while (true) {
      try {
        String input = Readline.readline("> ");
        if (input != null) run(input);
      } catch (EOFException e) {
        break;
      }
      errors = 0;
    }
    System.out.println();
  }
}
