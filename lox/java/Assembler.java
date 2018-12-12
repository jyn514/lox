package lox.java;

import java.util.List;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Files;

class Assembler extends Pass<List<String>, Void> {

  Assembler(List<String> asm) {
    super(asm);
  }

  Void runPass() {
    try {
      Path tmp = Files.createTempFile("lox-llvm-asm", null);
      PrintWriter writer = new PrintWriter(Files.newBufferedWriter(tmp));

      for (String s : input) {
        writer.println(s);
      }
      writer.flush();

      System.err.println("Wrote asm to file " + tmp);

      ProcessBuilder lli = new ProcessBuilder("lli", "-color", tmp.toString())
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT);

      int ret = lli.start().waitFor();
      if (ret != 0) System.exit(ret);
    } catch (InterruptedException e) {
      System.exit(130);
    } catch (IOException e) {
      System.err.println("Failed to write to file during assembly");
    }

    return null;
  }
}
