package de.leidenheit.infrastructure.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class ResolverUtils {

    public static JsonNode getNestedValue(final JsonNode resolveNode, final String keyPath) {
        String[] keys = keyPath.split("\\.");
        JsonNode currentNode = resolveNode;
        for (String key : keys) {
            if (currentNode.has(key)) {
                currentNode = currentNode.get(key);
            } else {
                return null;
            }
        }
        return currentNode;
    }

    public static Object getNestedValue(final Map<String, Object> resolveMap, final String keyPath) {
        String[] keys = keyPath.split("\\.");
        Object current = resolveMap;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    private ResolverUtils() {}
}
