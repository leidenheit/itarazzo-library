package de.leidenheit.infrastructure.parsing;

import de.leidenheit.core.exception.ItarazzoInterruptException;
import de.leidenheit.core.exception.ItarazzoUnsupportedException;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@Slf4j
public class SourceDescriptionInitializer {

    public static void initialize(final ArazzoSpecification arazzo) {
        arazzo.getSourceDescriptions().forEach(sourceDescription -> {
            switch (sourceDescription.getType()) {
                case OPENAPI:
                    initializeAsOpenAPI(sourceDescription);
                    break;
                case ARAZZO:
                    initializeAsArazzo(sourceDescription);
                    break;
                default:
                    throw new ItarazzoUnsupportedException(
                            "Source Description of type: %s".formatted(sourceDescription.getType()));
            }
        });
    }

    private static void initializeAsOpenAPI(final SourceDescription sourceDescription) {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        io.swagger.v3.parser.core.models.ParseOptions options = new io.swagger.v3.parser.core.models.ParseOptions();
        options.setResolveFully(true);
        options.setOaiAuthor(false);
        OpenAPI openAPI = parser.read(sourceDescription.getUrl(), Collections.emptyList(), options);
        sourceDescription.setReferencedOpenAPI(openAPI);
    }

    private static void initializeAsArazzo(final SourceDescription sourceDescription) {
        Parser parser = new Parser();
        var options = ParseOptions.ofDefault();
        var refArazzoParseResult = parser.readLocation(sourceDescription.getUrl(), options);
        if (!refArazzoParseResult.getMessages().isEmpty()) {
            log.info("Parsing report of source '%s':%n%s".formatted(sourceDescription.getName(), String.join("\n", refArazzoParseResult.getMessages())));
        }
        if (refArazzoParseResult.isInvalid()) throw new ItarazzoInterruptException("Parsing failed");
        sourceDescription.setReferencedArazzo(refArazzoParseResult.getArazzo());
    }

    private SourceDescriptionInitializer() {
    }
}
