package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workflow {
    private String workflowId;
    private String summary;
    private String description;
    private JsonNode inputs;
    private List<String> dependsOn;
    private List<Step> steps;
    private List<SuccessAction> successActions;
    private List<FailureAction> failureActions;
    private Map<String, Object> outputs;
    private List<Parameter> parameters;
    private Map<String, Object> extensions;
}
