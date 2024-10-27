package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArazzoSpecification {

    private String arazzo;
    private Info info;
    private List<SourceDescription> sourceDescriptions;
    private List<Workflow> workflows;
    private Components components;
    private Map<String, Object> extensions;
}
