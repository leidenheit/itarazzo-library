package de.leidenheit.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.*;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourceDescription {
    private String name;
    private String url;
    @Builder.Default
    private SourceDescriptionType type = SourceDescriptionType.OPENAPI;
    private Map<String, Object> extensions;

    private OpenAPI referencedOpenAPI;
    private ArazzoSpecification referencedArazzo;

    @Getter
    @AllArgsConstructor
    public enum SourceDescriptionType {
        OPENAPI("openapi"),
        ARAZZO("arazzo");

        private final String value;
    }
}
