package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FailureAction {
    private String name;
    private FailureActionType type;
    private String workflowId;
    private String stepId;
    private BigDecimal retryAfter; // non negative
    private Integer retryLimit; // non negative
    private List<Criterion> criteria;
    private Map<String, Object> extensions;

    @Getter
    @AllArgsConstructor
    public enum FailureActionType {
        END("end"),
        RETRY("retry"),
        GOTO("goto");

        private final String value;
    }
}
