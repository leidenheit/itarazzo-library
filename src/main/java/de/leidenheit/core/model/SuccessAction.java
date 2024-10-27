package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SuccessAction {
    private String name;
    private SuccessActionType type;
    private String workflowId;
    private String stepId;
    private List<Criterion> criteria;
    private Map<String, Object> extensions;

    @Getter
    @AllArgsConstructor
    public enum SuccessActionType {
        END("end"),
        GOTO("goto");

        private final String value;
    }
}
