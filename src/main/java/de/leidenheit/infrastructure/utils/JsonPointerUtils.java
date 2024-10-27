package de.leidenheit.infrastructure.utils;

public class JsonPointerUtils {

    private JsonPointerUtils() {}

    // from e.g. '{$sourceDescriptions.cookieApi.url}#/paths/~1cookies~1{id}/get'
    public static String[] extractPathAndOperationFromJsonPointer(final String jsonPointer) {
        if (jsonPointer == null || !jsonPointer.startsWith("#/")) {
            throw new IllegalArgumentException("Invalid JSON-Pointer: %s".formatted(jsonPointer));
        }

        // remove leading "#/" and get tokens
        String pointer = jsonPointer.substring(2);
        String[] tokens = pointer.split("/");
        if (tokens.length < 3 || !"paths".equals(tokens[0])) {
            throw new IllegalArgumentException("JSON-Pointer does not adhere to valid path: %s".formatted(jsonPointer));
        }

        String pathToken = tokens[1]; // actual path
        String path = unescapeJsonPointer(pathToken);

        String operation = tokens[2]; // actual operation

        return new String[]{path, operation};
    }

    public static String unescapeJsonPointer(final String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }
}
