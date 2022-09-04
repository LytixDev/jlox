package dev.lytix.lox;

import java.util.ArrayList;
import java.util.List;

import static dev.lytix.lox.TokenType.*;

/*
 * recursive descent parser.
 *
 * complete context-free expression grammar:
 *
 * program      -> declaration* EOF ;
 *
 * declaration  -> varDecl
 *              | statement ;
 *
 * statement    -> exprStmt
 *              |  printStmt ;
 *              |  block
 *
 * block        -> "{" declaration* "}" ;
 *
 * varDecl      -> "var" IDENTIFIER ( "=" expression )? ";" ;
 *
 * exprStmt     -> expression ";" ;
 * printStmt    -> "print" expression ";" ;
 *
 * expression   -> assignment ;
 * assignment   -> IDENTIFIER "=" assignment
 *              |  equality ;
 * equality     -> comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison   -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term         -> factor ( ( "-" | "+" ) factor )* ;
 * factor       -> unary ( ( "/" | "*" ) unary )* ;
 * unary        -> ( "!" | "-") unary
 *              |  primary;
 * primary      -> "true" | "false" | "nil"
 *              |  NUMBER | STRING
 *              |  "(" expression ")"
 *              |  IDENTIFIER ;
 *
 */
public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    /* points to the current token that is being parsed */
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd())
            statements.add(declaration());

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        /*
         * statement    -> exprStmt
         *              |  printStmt ;
         *              |  block
         */
        if (match(PRINT)) return printStatement();

        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private List<Stmt> block() {
        /* block        -> "{" declaration* "}" ; */
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd())
            statements.add(declaration());

        consume(RIGHT_BRACE, "Expected '}' after block.");
        return statements;
    }

    private Stmt varDeclaration() {
        /*
         * varDecl      -> "var" IDENTIFIER ( "=" expression )? ";" ;
         */
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL))
            initializer = expression();

        consume(SEMICOLON, "Expected ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt expressionStatement() {
        /*
         * exprStmt     -> expression ";" ;
         */
        Expr expr = expression();
        consume(SEMICOLON, "Exprected ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Stmt printStatement() {
        /*
         * printStmt    -> "print" expression ";" ;
         */
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Expr expression() {
        /* expression   -> assignment ; */
        return assignment();
    }

    private Expr assignment() {
        Expr expr = equality();

        /*
         * every assignment target can be valid syntax for normal expression.
         * parse left-hand side of assignment as if it were an expression.
         */
        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr equality() {
        /* equality     -> comparison ( ( "!=" | "==" ) comparison )* ; */
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /* equality */
    private Expr comparison() {
        /* comparison   -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ; */
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /* plus or minus */
    private Expr term() {
        /* term         -> factor ( ( "-" | "+" ) factor )* ; */
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /* multiplication or division */
    private Expr factor() {
        /* factor       -> unary ( ( "/" | "*" ) unary )* ; */
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        /* unary        -> ( "!" | "-") unary
                        |  primary; */
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        /*
         * primary      -> "true" | "false" | "nil"
         *              |  NUMBER | STRING
         *              |  "(" expression ")"
         *              |  IDENTIFIER ;
         */
        if (match(NUMBER, STRING))
            return new Expr.Literal(previous().literal);

        if (match(IDENTIFIER)) return new Expr.Variable(previous());

        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            /* consume until we find closing parenthesis */
            consume(RIGHT_PAREN, "Expected ')' after expression.");
            return new Expr.Grouping(expr);
        }

        /* entering here means token can't start an expression */
        throw error(peek(), "Expected expression.");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        //TODO: if (current == 0) return error;
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        /* discard tokens until we are at the beginning of the next statement */
        advance();

        while (!isAtEnd()) {
            /* semicolon signifies statement ending */
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN:
                    return;
            }

            advance();
        }
    }


}
