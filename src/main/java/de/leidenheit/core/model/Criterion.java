package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Criterion {
    private String condition;
    private String context;
    private CriterionType type;
    private CriterionExpressionTypeObject expressionTypeObject;
    private Map<String, Object> extensions;

    public void setType(final CriterionType type) {
        this.type = type;

        // handle jsonpath and xpath
        // see: https://spec.openapis.org/arazzo/latest.html#criterion-expression-type-object
        if (CriterionType.JSONPATH.equals(type)) {
            this.expressionTypeObject = CriterionExpressionTypeObject.builder()
                    .type(CriterionExpressionTypeObject.CriterionExpressionType.JSONPATH)
                    .build();
        } else if (CriterionType.XPATH.equals(type)) {
            this.expressionTypeObject = CriterionExpressionTypeObject.builder()
                    .type(CriterionExpressionTypeObject.CriterionExpressionType.XPATH)
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor
    public enum CriterionType {
        SIMPLE("simple"),
        REGEX("regex"),
        JSONPATH("jsonpath"),
        XPATH("xpath");

        private final String value;
    }
}
