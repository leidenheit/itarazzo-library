package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Components {
    private Map<String, JsonNode> inputs;
    private Map<String, Parameter> parameters;
    private Map<String, SuccessAction> successActions;
    private Map<String, FailureAction> failureActions;
    private Map<String, Object> extensions;
}
