package lox.java;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

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
        for (Token token : new Lexer(input).scanTokens()) {
            System.out.println(token);
        }
    }

    public static void error(int line, int column, String message) {
        errors++;
        System.err.println(("" + line) + ':' + column
                + ": error: " + message);
    }

    private static void runFile(String path) throws IOException {
        run(new String(Files.readAllBytes(Paths.get(path))));
        if (errors > 0) {
            System.out.print("" + errors + " error");
            if (errors > 1) System.out.println('s');
            else System.out.println();
            System.exit(2);
        }
    }

    private static void runPrompt() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("> ");
        String input;
        while ((input = reader.readLine()) != null) {
            run(input);
            System.out.print("> ");
        }
    }
}
