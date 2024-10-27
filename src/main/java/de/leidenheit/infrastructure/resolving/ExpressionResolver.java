package de.leidenheit.infrastructure.resolving;

public interface ExpressionResolver {

    Object resolveExpression(final String expression, final ResolverContext context);
}
