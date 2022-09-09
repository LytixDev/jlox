package dev.lytix.lox;

import java.util.List;

public interface LoxCallable {
    /*
     * pass in the interpreter as the class implementing call() may need it
     * returns the value that the expression produces.
     */
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);

}
