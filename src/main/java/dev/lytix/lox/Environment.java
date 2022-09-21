package dev.lytix.lox;

import dev.lytix.lox.exceptions.RuntimeError;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    public Environment() {
        enclosing = null;

    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    Object get(Token name) {
        /* check local scope first */
        if (values.containsKey(name.lexeme))
            return values.get(name.lexeme);

        if (enclosing != null)
            return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" +
            name.lexeme + "'.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    Environment ancestor(int distance) {
        Environment environment = this;

        for (int i = 0; i < distance; i++)
            environment = environment.enclosing;

        return environment;
    }

    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        /* recursively check the enclosing scopes */
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        /* cannot assign variable that has not been defined */
        throw new RuntimeError(name, "Undefined variable '" +
                name.lexeme + "'.");
    }
}
