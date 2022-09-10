package dev.lytix.lox.natives;

import dev.lytix.lox.Interpreter;
import dev.lytix.lox.LoxCallable;

import java.util.List;

public class ClockNative extends Native implements LoxCallable {
    @Override
    public int arity() {
        return 0;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
    }
}
