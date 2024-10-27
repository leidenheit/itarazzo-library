package de.leidenheit.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * https://spec.openapis.org/arazzo/latest.html#reusable-object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReusableObject {
    private Object reference;
    private String value;
}
