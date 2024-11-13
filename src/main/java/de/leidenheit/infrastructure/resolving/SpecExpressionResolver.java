package de.leidenheit.infrastructure.resolving;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.utils.ResolverUtils;
import lombok.SneakyThrows;

import java.util.*;

public class SpecExpressionResolver extends HttpContextExpressionResolver {

    private final ResolvedExpressionProvider expressionProvider;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs = new HashMap<>();
    private final ArrayNode steps = mapper.createArrayNode();
    private final ArrayNode workflows = mapper.createArrayNode();
    private final ArrayNode sourceDescriptions = mapper.createArrayNode();

    public SpecExpressionResolver(final ArazzoSpecification arazzo, final Map<String, Object> inputs) {
        this.inputs = inputs;
        this.sourceDescriptions.addAll(Objects.requireNonNull(
                mapper.convertValue(arazzo.getSourceDescriptions(), ArrayNode.class)));
        this.workflows.addAll(Objects.requireNonNull(
                mapper.convertValue(arazzo.getWorkflows(), ArrayNode.class)));
        arazzo.getWorkflows().forEach(workflow ->
                this.steps.addAll(Objects.requireNonNull(
                        mapper.convertValue(workflow.getSteps(), ArrayNode.class))));
        expressionProvider = ResolvedExpressionProvider.getInstance();
    }

    @SneakyThrows
    @Override
    public Object resolveExpression(final String expression, final ResolverContext context) {
        // re-use already resolved expressions
        Object resolved = findResolved(expression);

        if (Objects.isNull(resolved)) {
            if (expression.startsWith("$inputs.")) {
                resolved = ResolverUtils.getNestedValue(inputs, expression.substring("$inputs.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$outputs.")) {
                resolved = ResolverUtils.getNestedValue(outputs, expression.substring("$outputs.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$sourceDescriptions.")) {
                resolved = resolveSourceDescription(sourceDescriptions, expression.substring("$sourceDescriptions.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$workflows.")) {
                resolved = resolveWorkflows(workflows, expression.substring("$workflows.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$steps.")) {
                resolved = resolveSteps(steps, expression.substring("$steps.".length()));
                if (Objects.nonNull(resolved) && resolved instanceof TextNode resolvedAsTextNode) {
                    resolved = resolvedAsTextNode.asText();
                }
            } else if (expression.startsWith("$components.") || expression.startsWith("#/components")) {
                throw new ItarazzoIllegalStateException("Expected to be handled by ArazzoComponentRefResolver but was not: %s"
                        .formatted(expression));
            } else {
                resolved = super.resolveExpression(expression, context);
                if (resolved instanceof String resolvedAsString) {
                    return resolvedAsString;
                } else {
                    // TODO refactor
                    return mapper.writeValueAsString(resolved);
                }
            }

            if (Objects.nonNull(resolved) && !expression.equalsIgnoreCase(resolved.toString())) {
                // add resolved expression to reference map
                addResolved(expression, resolved);
            }
        }
        return resolved;
    }

    public String resolveString(final String expression) {
        StringBuilder result = new StringBuilder();
        if (expression.contains("{$")) {
            int start = 0;
            while (start < expression.length()) {
                int openIndex = expression.indexOf("{$", start);
                if (openIndex == -1) {
                    result.append(expression.substring(start));
                    break;
                }
                result.append(expression, start, openIndex);
                int closeIndex = expression.indexOf('}', openIndex);
                if (closeIndex == -1) {
                    throw new IllegalArgumentException("Unmatched '{$' in expression: " + expression);
                }
                String expr = expression.substring(openIndex + 1, closeIndex);
                Object resolved = resolveExpression(expr, null);
                if (Objects.nonNull(resolved)) {
                    if (resolved instanceof TextNode textNode) {
                        result.append(textNode.asText());
                    } else {
                        result.append(resolved);
                    }
                } else {
                    throw new ItarazzoIllegalStateException("Tried to resolve expression %s but got null".formatted(expr));
                }
                start = closeIndex + 1;
            }
        } else {
            result.append(resolveExpression(expression, null));
        }
        return result.toString();
    }

    public void addResolved(final String key, final Object resolved) {
        this.expressionProvider.addResolved(key, resolved);
    }

    public Object findResolved(final String expression){
        return expressionProvider.findResolved(expression);
    }

    private JsonNode resolveSourceDescription(final ArrayNode sourceDescriptionsArray, final String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : sourceDescriptionsArray) {
            if (sourceNode.has("name") && sourceNode.get("name").asText().equals(targetName)) {
                return ResolverUtils.getNestedValue(sourceNode, String.join(".", targetFields));
            }
        }
        return null;
    }


    private JsonNode resolveSteps(final ArrayNode stepsArray, final String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : stepsArray) {
            if (sourceNode.has("stepId") && sourceNode.get("stepId").asText().equals(targetName)) {
                var nestedKeyPath = String.join(".", targetFields);
                var resolved = ResolverUtils.getNestedValue(sourceNode, nestedKeyPath);
                if (Objects.nonNull(resolved) && resolved.isTextual()) {
                    resolved = new TextNode(resolveString(resolved.asText()));
                    return resolved;
                }
                throw new ItarazzoIllegalStateException("Tried to resolved nested key path %s but got null".formatted(nestedKeyPath));
            }
        }
        return null;
    }

    private JsonNode resolveWorkflows(final ArrayNode stepsArray, final String keyPath) {
        String[] keys = keyPath.split("\\.");

        if (keys.length < 2) return null;

        String targetName = keys[0];
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);

        for (JsonNode sourceNode : stepsArray) {
            if (sourceNode.has("workflowId") && sourceNode.get("workflowId").asText().equals(targetName)) {
                var nestedKeyPath = String.join(".", targetFields);
                var resolved = ResolverUtils.getNestedValue(sourceNode, nestedKeyPath);
                if (Objects.nonNull(resolved) && resolved.isTextual()) {
                    resolved = new TextNode(resolveString(resolved.asText()));
                    return resolved;
                }
                throw new ItarazzoIllegalStateException("Tried to resolved nested key path %s but got null".formatted(nestedKeyPath));
            }
        }
        return null;
    }

    private void resolveJsonObject(final ObjectNode node) {
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                var resolved = resolveString(value.asText());
                node.put(entry.getKey(), resolved);
            } else if (value.isObject()) {
                resolveJsonObject((ObjectNode) value);
            } else if (value.isArray()) {
                resolveJsonArray((ArrayNode) value);
            }
        });
    }

    private void resolveJsonArray(final ArrayNode arrayNode) {
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode value = arrayNode.get(i);
            if (value.isTextual()) {
                arrayNode.set(i, new TextNode(resolveString(value.asText())));
            } else if (value.isObject()) {
                resolveJsonObject((ObjectNode) value);
            } else if (value.isArray()) {
                resolveJsonArray((ArrayNode) value);
            }
        }
    }
}
