package lox.java;

import java.util.List;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Files;

class Assembler extends Pass<List<String>, Void> {
  private static final String LLVM_DOWNLOAD_PAGE = "http://releases.llvm.org/download.html#7.0.0";
  private static final boolean onWindows = System.getProperty("os.name").toLowerCase().contains("windows");

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

      ProcessBuilder lli = makeCommand("lli", "-color", tmp.toString());

      try {
        int ret = lli.start().waitFor();
        if (ret != 0) System.exit(ret);
      } catch(IOException e) {
        System.err.println("Could not find LLVM interpreter. "
            + "You may need to install it from " + LLVM_DOWNLOAD_PAGE + " or a package manager");
        if (onWindows)
          // TODO: document
          System.err.println("LLVM needs to be built from source on Windows; "
            + "the pre-built binary does not include llc or lli.");
      }
    } catch (InterruptedException e) {  // Ctrl-C
      System.exit(130);
    } catch (IOException e) {
      System.err.println("Failed to write to file during assembly");
    }

    return null;
  }

  private static ProcessBuilder makeCommand(String ... args) {
      return new ProcessBuilder(args)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT);
  }
}
