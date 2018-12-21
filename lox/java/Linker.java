package lox.java;

import java.io.IOException;

import static lox.java.Interpreter.makeCommand;

class Linker extends Pass<String, Void> {
  Linker(String input) {
    super(input);
  }

  @Override
  Void runPass() {
    ProcessBuilder clang = makeCommand("clang", "-Wno-override-module", "-o", "a.out", input);
    try {
      clang.start().waitFor();
    } catch (InterruptedException e) {
      System.exit(130);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}
