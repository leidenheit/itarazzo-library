package de.leidenheit.core.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Parameter {
    private String name;
    private ParameterIn in;
    private Object value;
    private String reference;
    private Map<String, Object> extensions;

    @Getter
    @AllArgsConstructor
    public enum ParameterIn {
        PATH("path"),
        HEADER("header"),
        QUERY("query"),
        BODY("body"),
        COOKIE("cookie");

        private final String value;
    }
}
