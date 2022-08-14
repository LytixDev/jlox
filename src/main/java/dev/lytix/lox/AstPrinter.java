package dev.lytix.lox;


/**
 * Pretty (ugly) printer
 */
public class AstPrinter implements Expr.Visitor<String> {
    String print(Expr expr) {
       return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    private String parenthesize(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder();
        builder.append("(").append(name);

        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }

    public static void main(String[] args) {
        /* test pretty printing */
        Token token1 = new Token(TokenType.MINUS, "-", null, 1);
        Token token2 = new Token(TokenType.STAR, "*", null, 1);
        Expr unary = new Expr.Unary(token1, new Expr.Literal(69));
        Expr grouping = new Expr.Grouping(new Expr.Literal(4.20));

        Expr expression = new Expr.Binary(unary, token2, grouping);
        System.out.println(new AstPrinter().print(expression));
    }
}
