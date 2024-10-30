package de.leidenheit.infrastructure.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.infrastructure.resolving.ResolvedExpressionProvider;

import java.util.Map;

public class ResolverUtils {

    public static JsonNode getNestedValue(final JsonNode resolveNode, final String keyPath) {
        String[] keys = keyPath.split("\\.");
        JsonNode currentNode = resolveNode;
        for (String key : keys) {
            if (currentNode.has(key)) {
                currentNode = currentNode.get(key);
                if (currentNode.asText().contains("$")) {
                    var resolved = ResolvedExpressionProvider.getInstance().findResolved(currentNode.asText());
                    currentNode = parseNestedNode(resolved.toString());
                }
            } else {
                return null;
            }
        }
        return currentNode;
    }

    private static JsonNode parseNestedNode(final String nodeAsString) {
        var jsonMapper = new ObjectMapper();
        var xmlMapper = new XmlMapper();
        try {
            if (nodeAsString.startsWith("{") || nodeAsString.startsWith("[")) {
                return jsonMapper.readTree(nodeAsString);
            }
            return xmlMapper.readTree(nodeAsString);
        } catch (Exception e) {
            throw new ItarazzoIllegalStateException(e);
        }
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
