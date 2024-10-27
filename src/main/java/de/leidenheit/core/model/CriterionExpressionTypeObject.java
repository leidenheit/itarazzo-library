package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CriterionExpressionTypeObject {
    private CriterionExpressionType type;
    private String version;
    private Map<String, Object> extensions;

    @Getter
    @AllArgsConstructor
    public enum CriterionExpressionType {
        JSONPATH("jsonpath"),
        XPATH("xpath");

        private final String value;
    }
}
