package dev.lytix.lox;

import java.util.ArrayList;
import java.util.Arrays;
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
 *              -> varGoDecl
 *              | statement ;
 *
 * statement    -> exprStmt
 *              |  forStmt
 *              |  ifStmt
 *              |  printStmt
 *              |  whileStmt
 *              |  block ;
 *
 * forStmt      -> "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
 *
 * whileStmt    -> "while" "(" expression ")" statement ;
 *
 * ifStmt       -> "if" "(" expression ")" statement ( "else" statement )? ;
 *
 * block        -> "{" declaration* "}" ;
 *
 * declaration  -> funDecl
 *              |  varDecl
 *              |  varGoDecl
 *              |  statement ;
 *
 * funDecl      -> "fun" function ;
 * function     -> IDENTIFIER "(" parameters? ")" block ;
 * parameters   -> IDENTIFIER ( "," IDENTIFIER )* ;
 *
 * varDecl      -> "var" IDENTIFIER ( "=" expression )? ";" ;
 * varGoDecl    -> IDENTIFIER ":=" expression ";" ;
 *
 * exprStmt     -> expression ";" ;
 * printStmt    -> "print" expression ";" ;
 *
 * expression   -> assignment ;
 * assignment   -> IDENTIFIER "=" assignment
 *              |  equality
 *              |  logic_or ;
 *
 * logic_or     -> logic_and ( "or" logic_and )* ;
 * logic_and    -> equality ( "and" equality )* ;
 *
 * equality     -> comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison   -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term         -> factor ( ( "-" | "+" ) factor )* ;
 * factor       -> unary ( ( "/" | "*" ) unary )* ;
 * unary        -> ( "!" | "-") unary
 *              |  call;
 *
 * call         -> primary ( "(" arguments? ")" )* ;
 * arguments    -> expression ( "," expression )* ;
 *
 *
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
            if (match(FUN))
                return function("function");

            if (match(VAR))
                return varDeclaration();

            if (match(IDENTIFIER))
                if (check(COLON_EQUAL))
                    return varGoDeclaration();
                else
                    current--;

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        /*
         * statement    -> exprStmt
         *              |  forStmt
         *              |  ifStmt
         *              |  printStmt
         *              |  whileStmt
         *              |  block ;
         */
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();

        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt forStatement() {
        /* forStmt      -> "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ; */

        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON))
            /* initializer has been omitted */
            initializer = null;
        else if (match(VAR))
            initializer = varDeclaration();
        else
            /* wrap expr in stmt */
            initializer = expressionStatement();

        Expr condition = null;
        if (!check(SEMICOLON))
            condition = expression();
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN))
            increment = expression();
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        /* desugar for loop syntax sugar into a while loop in the AST */
        if (increment != null)
            body = new Stmt.Block(Arrays.asList(body,
                   new Stmt.Expression(increment)));

        /* if condition is omitted, fall back to 'true' */
        if (condition == null)
            condition = new Expr.Literal(true);

        body = new Stmt.While(condition, body);

        if (initializer != null)
            body = new Stmt.Block(Arrays.asList(initializer, body));

        return body;
    }

    private Stmt whileStatement() {
        /* whileStmt    -> "while" "(" expression ")" statement ; */
        consume(LEFT_PAREN, "Expect '(' after 'while'");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after while condition");

        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt ifStatement() {
        /* ifStmt       -> "if" "(" expression ")" statement ( "else" statement )? ; */
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE))
            elseBranch = statement();

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private List<Stmt> block() {
        /* block        -> "{" declaration* "}" ; */
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd())
            statements.add(declaration());

        consume(RIGHT_BRACE, "Expected '}' after block.");
        return statements;
    }


    private Stmt.Function function(String kind) {
        /* function     -> IDENTIFIER "(" parameters? ")" block ; */
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();

        /* handle zero parameter case */
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255)
                    error(peek(), "Can't have more than 255 parameters.");

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        /* block() assumes the brace token has already been matched */
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
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

    private Stmt varGoDeclaration() {
        /*
         * varGoDecl    -> IDENTIFIER ":=" expression ";" ;
         */
        Token name = previous();
        consume(COLON_EQUAL, "Internal error: saw ':=', but could not consume it.");

        Expr initializer = expression();
        consume(SEMICOLON, "Expected ';' after ':='.");
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
        /* assignment   -> IDENTIFIER "=" assignment
         *              |  equality
         *              |  logic_or ;
         */
        Expr expr = or();

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

    private Expr or() {
        /* logic_or     -> logic_and ( "or" logic_and )* ; */
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        /* logic_and    -> equality ( "and" equality )* ; */
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
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
                        |  call; */
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();

        /* function calls can have no argument, so only parse arguments when no right paren */
        if (!check(RIGHT_PAREN)) {
            do {
                /* a function can't accept no more than 255 arguments */
                if (arguments.size() > 255)
                    /* do not throw error, keep parsing */
                    error(peek(), "Can't have more than 255 arguments to a function.");

                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after function arguments.");
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr call() {
        /* call         -> primary ( "(" arguments? ")" )* ; */
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN))
                /* use previously parsed expression as callee, recursively */
                expr = finishCall(expr);
            else
                break;
        }

        return expr;
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

    @Override
    public String toString() {
        return "Parser{" +
                "tokens=" + tokens +
                ", current=" + current +
                '}';
    }
}
