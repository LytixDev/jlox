package dev.lytix.lox;

import dev.lytix.lox.exceptions.RuntimeError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static Interpreter interpreter;
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 1 ) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            interpreter = new Interpreter(false);
            runFile(args[0]);
        } else {
            interpreter = new Interpreter(true);
            runPrompt();
        }
    }
    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);
        System.out.println("lox REPL");
        final String ps1 = ">>>";
        String line;

        while (true) {
            if (hadError || hadRuntimeError)
                System.out.println("\n");

            hadError = false;
            hadRuntimeError = false;

            System.out.print(ps1 + " ");
            line = reader.readLine();
            if (line == null) break;
            run(line);
        }

        System.out.println("Bye!");
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        /* stop if there was a syntax error */
        if (hadError) return;

        interpreter.interpret(statements);
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF)
            report(token.line, " at end", message);
        else
            report(token.line, " at '" + token.lexeme + "'", message);
    }

    static void runtimeError(RuntimeError error) {
        if (error.token != null) {
            System.err.println(error.getMessage() +
                    "\n[Line " + error.token.line + "]");
        } else {
            System.err.println(error.getMessage() + "\n");
        }
        hadRuntimeError = true;
    }

    static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error " + where + ": " + message);
        hadError = true;
    }
}

