package de.leidenheit.infrastructure.parsing;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
// TODO introduce parameterized handling from POM or equivalent
public class ParseOptions {

    private final boolean oaiAuthor;
    private final boolean allowEmptyStrings;
    private final boolean mustValidate; // TODO implementation
    private final boolean resolve; // TODO implementation

    public static ParseOptions ofDefault() {
        return ParseOptions.builder()
                .oaiAuthor(false)
                .allowEmptyStrings(false)
                .mustValidate(true)
                .resolve(true)
                .build();
    }
}
