package lox.java;

import java.io.IOException;

class Interpreter extends Pass<String, Void> {
  private static final String LLVM_DOWNLOAD_PAGE = "http://releases.llvm.org/download.html#7.0.0";
  private static final boolean onWindows = System.getProperty("os.name").toLowerCase().contains("windows");

  Interpreter(String input) {
    super(input);
  }

  Void runPass() {
    ProcessBuilder lli = makeCommand("lli", "-color", input);

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
    } catch (InterruptedException e) {  // Ctrl-C
      System.exit(130);
    }

    return null;
  }

  static ProcessBuilder makeCommand(String ... args) {
      return new ProcessBuilder(args)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT);
  }
}
