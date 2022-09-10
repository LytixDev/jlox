package dev.lytix.lox;

import dev.lytix.lox.exceptions.Return;
import dev.lytix.lox.exceptions.RuntimeError;
import dev.lytix.lox.natives.ClockNative;
import dev.lytix.lox.natives.FreadNative;

import java.util.ArrayList;
import java.util.List;

import static dev.lytix.lox.TokenType.*;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    final Environment globals = new Environment();
    private Environment environment = globals;
    boolean REPL = false;

    public Interpreter(boolean REPL) {
        this.REPL = REPL;

        /* adds the language native functions to the global scope */
        includeNativeFunctions();
    }

    void includeNativeFunctions() {
        /* add clock native */
        globals.define("clock", new ClockNative());
        /* add fread native */
        globals.define("fread", new FreadNative());
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements)
                execute(statement);
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            /* fresh env */
            this.environment = environment;
            for (Stmt statement : statements)
                execute(statement);
        } finally {
            /* update env again */
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;

            /* equality supports types of any kind */
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);

            /* plus supports both number addition and string concatenation */
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                    return (double) left + (double) right;
                if (left instanceof String && right instanceof String)
                    return (String) left + (String) right;

                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
        }
        /* unreachable */
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments)
            arguments.add(evaluate(argument));

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren,
                    "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;
        if (arguments.size() != function.arity())
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");

        return function.call(this, arguments);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        Object res = evaluate(stmt.expression);
        //if (REPL)
        //    System.out.println(stringify(res));
        /* java stupid */
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment);
        /* add function to namespace */
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        Object condition = evaluate(stmt.condition);
        if (isTruthy(condition))
            stmt.thenBranch.accept(this);
        else if (stmt.elseBranch != null)
            stmt.elseBranch.accept(this);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);

        /* stop interpreting function body and return*/
        throw new Return(value);
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition)))
            execute(stmt.body);

        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null)
            value = evaluate(stmt.initializer);

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        Object res = environment.get(expr.name);
        //if (res == null)
        //    throw new RuntimeError(expr.name, "Attempting to access a variable that is not initialized");

        return res;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    /*
     * returns the first operator with appropriate truthiness.
     * TODO: could be interesting to return both?
     *       that would allow syntax like:
     *       print "hello " and "world!";
     *       >>>hello world!;
     *       Or maybe this is stupid as it would come with unexpected side effects
     *       in most cases. Besides, the + operator can be used for joining to strings.
     */
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            /* skip checking right side if left is truthy */
            if (isTruthy(left))
                return left;
        } else {
            /* skip checking right side of and if left already is false */
            if (!isTruthy(left))
                return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        if (expr.operator.type == BANG)
            return !isTruthy(right);
        if (expr.operator.type == MINUS) {
            checkNumberOperand(expr.operator, right);
            return -(double) right;
        }

        /* unreachable */
        return null;
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    /* 'false', 'nil', empty string '""' and '0' are falsey, everything else is truthy */
    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        if (object instanceof Double) return (double)object != 0.0;
        if (object instanceof String) return !((String)object).equals("");
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            /* treat precise double as integer */
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

}
