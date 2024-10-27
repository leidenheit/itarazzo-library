package de.leidenheit.infrastructure.resolving;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("java:S6548")
public class ResolvedExpressionProvider {

    private static ResolvedExpressionProvider instance;
    private final Map<String, Object> resolvedExpressionsMap = new HashMap<>();
    
    public static synchronized ResolvedExpressionProvider getInstance() {
        if (Objects.isNull(instance)) {
            instance = new ResolvedExpressionProvider();
        }
        return instance;
    }

    public void addResolved(final String expression, final Object resolved) {
        this.resolvedExpressionsMap.put(expression, resolved);
    }

    public Object findResolved(final String expression) {
        return this.resolvedExpressionsMap.get(expression);
    }

    private ResolvedExpressionProvider() {}
}
