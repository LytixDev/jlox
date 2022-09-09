package dev.lytix.lox.natives;

import dev.lytix.lox.Interpreter;
import dev.lytix.lox.LoxCallable;
import dev.lytix.lox.RuntimeError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class FreadNative extends Native implements LoxCallable {
    @Override
    public int arity() {
        return 1;
    }

    public Object call(Interpreter interpreter, List<Object> arguments) {
        if (!(arguments.get(0) instanceof String fileName))
            throw new RuntimeError(null,
                    "First argument to fread native must be a string.");

        String result = null;
        try {
            result = Files.readString(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeError(null, "Path: " + fileName +
                    " was not found or could not be opened.");
        }

        return result;
    }
}
