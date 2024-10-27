package de.leidenheit.infrastructure.validation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
// TODO introduce parameterized handling from POM or equivalent
public class ValidationOptions {
    private boolean failFast;
    private boolean validateReferences;

    public static ValidationOptions ofDefault() {
        return ValidationOptions.builder()
                .failFast(false)
                .validateReferences(false)
                .build();
    }
}
