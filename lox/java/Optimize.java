package lox.java;

import java.io.IOException;

import static lox.java.Interpreter.makeCommand;
import static lox.java.Lox.error;

class Optimize extends Pass<String, String> {
  Optimize(String input) {
    super(input);
  }

  @Override
  String runPass() {
    // optimize in-place; output llvm assembly
    ProcessBuilder opt = makeCommand("opt", input, "-S", "-o", input);

    try {
      opt.start().waitFor();
    } catch (InterruptedException e) {
      System.exit(130);
    } catch (IOException e) {
      error(-1, -1, "Failed to optimize file " + input);
    }
    return input;
  }
}
