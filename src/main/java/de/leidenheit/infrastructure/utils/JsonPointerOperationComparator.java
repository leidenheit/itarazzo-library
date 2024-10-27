package de.leidenheit.infrastructure.utils;

import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonPointerOperationComparator {

    private JsonPointerOperationComparator() {}

    // e.g '{$sourceDescriptions.cookieApi.url}#/paths/~1cookies~1{id}/get'
    public static boolean compareJsonPointerToPathAndOperation(final String operationPathJsonPointer,
                                                               final Paths paths) {
        try {
            Pattern pattern = Pattern.compile("#/[^']+");
            Matcher matcher = pattern.matcher(operationPathJsonPointer);
            if (!matcher.find()) throw new ItarazzoIllegalStateException(
                    "Pattern matching failed: input='%s' pattern='%s'".formatted(operationPathJsonPointer, pattern));

            var validJsonPointerOfOperationPath = matcher.group();

            String[] extracted = JsonPointerUtils.extractPathAndOperationFromJsonPointer(validJsonPointerOfOperationPath);
            String extractedPath = extracted[0];
            String extractedOperation = extracted[1];

            var res = paths.entrySet().stream()
                    .filter(entry -> entry.getValue().readOperations().stream()
                            .anyMatch(o -> {
                                var pathItemMethod = PathItem.HttpMethod.valueOf(extractedOperation.toUpperCase());
                                return extractedPath.equals(entry.getKey()) &&
                                        Objects.nonNull(entry.getValue().readOperationsMap().getOrDefault(pathItemMethod, null));
                            })
                    )
                    .findFirst()
                    .orElseThrow(() -> new ItarazzoIllegalStateException("No operation found for path " + extractedPath));
            return Objects.nonNull(res);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
