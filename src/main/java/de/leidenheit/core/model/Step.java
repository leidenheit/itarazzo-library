package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class Step {
    private String description;
    private String stepId;
    private String operationId;
    private String operationPath;
    private String workflowId;
    private List<Parameter> parameters;
    private RequestBody requestBody;
    private List<Criterion> successCriteria;
    private List<SuccessAction> onSuccess;
    private List<FailureAction> onFailure;
    private Map<String, Object> outputs;
    private Map<String, Object> extensions;
}
