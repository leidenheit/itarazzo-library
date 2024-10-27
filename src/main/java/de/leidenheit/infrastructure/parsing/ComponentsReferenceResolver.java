package de.leidenheit.infrastructure.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import de.leidenheit.core.exception.ItarazzoIllegalArgumentException;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.exception.ItarazzoUnsupportedException;
import de.leidenheit.infrastructure.utils.ResolverUtils;

import java.util.Arrays;
import java.util.Objects;

public class ComponentsReferenceResolver {

    private static ComponentsReferenceResolver instance;

    private final JsonNode componentsNode;

    private ComponentsReferenceResolver(final JsonNode componentsNode) {
        this.componentsNode = componentsNode;
    }

    public static synchronized ComponentsReferenceResolver getInstance(final JsonNode componentsNode) {
        if (Objects.isNull(instance)) {
            instance = new ComponentsReferenceResolver(componentsNode);
        }
        return instance;
    }

    public JsonNode resolveComponent(final String reference) {
        if (reference.startsWith("#/components")) {
            return resolveJsonPointer(reference);
        } else if (reference.startsWith("$components.")) {
            return resolveRuntimeExpression(reference);
        }
        throw new ItarazzoUnsupportedException("Unsupported reference: %s".formatted(reference));
    }

    private JsonNode resolveJsonPointer(final String jsonPointer) {
        // convert JSON-Pointer to the right format
        String pointer = jsonPointer.replace("#/components", "");
        JsonNode result = componentsNode.at(pointer);
        if (result.isMissingNode()) throw new ItarazzoIllegalStateException(
                "JSON pointer into nowhere: pointer=%s".formatted(pointer));
        return result;
    }

    private JsonNode resolveRuntimeExpression(final String runtimeExpression) {
        String[] keys = runtimeExpression.split("\\.");
        if (keys.length < 2 || !keys[0].equals("$components")) throw new ItarazzoIllegalArgumentException(
                "Invalid expression: %s".formatted(runtimeExpression));
        String[] targetFields = Arrays.copyOfRange(keys, 1, keys.length);
        return ResolverUtils.getNestedValue(componentsNode, String.join(".", targetFields));
    }
}
