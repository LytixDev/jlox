package dev.lytix.lox;

import dev.lytix.lox.exceptions.Return;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;

    public LoxFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        /* fresh env for the function scope that takes in the closure that also holds the globals */
        Environment environment = new Environment(closure);

        /* copy arguments into env as their parameter name */
        for (int i = 0; i < declaration.params.size(); i++)
            environment.define(declaration.params.get(i).lexeme,
                    arguments.get(i));

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            /* stop interpreting function body and return the return value */
            return returnValue.value;
        }
        /* any function without an explicit return statement returns 'nil' by default */
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
