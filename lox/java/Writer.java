package lox.java;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static lox.java.Lox.error;

class Writer extends Pass<List<String>, String> {

  Writer(List<String> input) {
    super(input);
  }

  @Override
  String runPass() {
    try {
      Path tmp = Files.createTempFile("lox-llvm-asm", ".ll");
      BufferedWriter writer = Files.newBufferedWriter(tmp);
      writer.write(String.join("\n", input));
      writer.flush();

      System.err.println("Wrote asm to file " + tmp);
      return tmp.toString();
    } catch (IOException e) {
      error(-1, -1, "Failed to write assembly");
    }
    return null;
  }
}
