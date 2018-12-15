package lox.java;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.util.List;

import org.gnu.readline.Readline;
import org.gnu.readline.ReadlineLibrary;

public class Lox {
  private static int errors = 0;
  private static String filename;
  private static final List<Class<? extends Pass<?, ?>>> passes = List.of(
    Lexer.class, Parser.class, Annotate.class, Compiler.class, Assembler.class
  );

  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: jlox [file]");
      System.exit(1);
    }
    if (args.length == 1) {
      try {
        filename = args[0];
        runFile(new String(Files.readAllBytes(Paths.get(args[0]))));
      } catch (NoSuchFileException e) {
        System.err.println("File not found: " + args[0]);
      }
    } else if (System.console() == null) {
      filename = "<stdin>";
      runFile(readAllInput());
    } else {
      filename = "<stdin>";
      runPrompt();
    }
  }

  private static void run(String input) {
    // this definitely isn't horrifying at all
    Object result = input;
    for (Class<? extends Pass<?, ?>> pass : passes) {
      if (errors != 0) return;
      try {
        Pass<?, ?> instance = getInstance(pass, result);
        result = instance.runPass();
      } catch (ReflectiveOperationException e) {
        e.printStackTrace();
        System.exit(5);
      }
    }
  }

  private static String readAllInput() throws IOException {
    // https://stackoverflow.com/questions/309424/
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = System.in.read(buffer)) != -1) {
        result.write(buffer, 0, length);
    }
    return result.toString();
  }

  private static Pass<?, ?> getInstance(Class<? extends Pass<?, ?>> pass, Object input)
   throws ReflectiveOperationException {
    // I laugh in the face of despair
    Class<?> clazz = input.getClass();
    try {
      return pass.getDeclaredConstructor(clazz).newInstance(input);
    } catch (NoSuchMethodException e) {
      // TODO: make this recursive
      for (Class<?> interfac : clazz.getInterfaces()) {
        try {
          return pass.getDeclaredConstructor(interfac).newInstance(input);
        } catch (NoSuchMethodException f) {
          // this isn't thrown until we get to the end
        }
      }
      throw new NoSuchMethodException("Could not find a matching method");
    }
  }

  static void error(int line, int column, String message) {
    errors++;
    System.err.println(filename + ':' + line + ':' + column
        + ": error: " + message);
  }

  private static void runFile(String input) throws IOException {
    run(input);
    if (errors > 0) {
      System.err.print("" + errors + " error");
      if (errors > 1) System.err.println('s');
      else System.err.println();
      System.exit(2);
    }
  }

  private static void runPrompt() throws IOException {
    try {
        Readline.load(ReadlineLibrary.GnuReadline);
    } catch (UnsatisfiedLinkError e) {
        //System.err.println("Note: GNU Readline not found, using built-in Java libraries")
    }
    Readline.initReadline("Lox");
    Runtime.getRuntime().addShutdownHook(new Thread(Readline::cleanup));
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
