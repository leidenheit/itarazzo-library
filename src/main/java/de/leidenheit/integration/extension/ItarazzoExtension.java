package de.leidenheit.integration.extension;

import com.google.common.base.Strings;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.parsing.ParseOptions;
import de.leidenheit.infrastructure.parsing.Parser;
import de.leidenheit.infrastructure.parsing.SourceDescriptionInitializer;
import de.leidenheit.infrastructure.utils.WorkflowSorterUtils;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidatorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ItarazzoExtension implements BeforeAllCallback, ParameterResolver {

    private static final String PROPERTY_ARAZZO_FILE = "arazzo.file";
    private static final String PROPERTY_ARAZZO_INPUTS_FILE = "arazzo-inputs.file";

    private final Map<Class<?>, Object> supportedParameterTypes = new HashMap<>();

    @Override
    public void beforeAll(final ExtensionContext context) {
        readAndProvideInputs();
        readAndProvideArazzoSpecification();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return supportedParameterTypes.containsKey(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return supportedParameterTypes.get(parameterContext.getParameter().getType());
    }

    private Optional<String> readFromSystemProperties(final String property) {
        var propertyValue = System.getProperty(property);
        if (Strings.isNullOrEmpty(propertyValue)) return Optional.empty();

        log.info("Reading property '{}': {}", property, propertyValue);
        return Optional.of(propertyValue);
    }

    private void readAndProvideInputs() {
        var arazzoInputs = readFromSystemProperties(PROPERTY_ARAZZO_INPUTS_FILE)
                .orElseThrow(() -> new ItarazzoIllegalStateException("Inputs not found: %s"
                        .formatted(PROPERTY_ARAZZO_INPUTS_FILE)));

        supportedParameterTypes.put(String.class, arazzoInputs);
    }

    private void readAndProvideArazzoSpecification() {
        var arazzoPath = readFromSystemProperties(PROPERTY_ARAZZO_FILE)
                .orElseThrow(() -> new ItarazzoIllegalStateException("Arazzo not found: %s"
                        .formatted(PROPERTY_ARAZZO_FILE)));

        var arazzo = loadArazzoFromPath(arazzoPath);
        WorkflowSorterUtils.sortByDependencies(arazzo);

        supportedParameterTypes.put(ArazzoSpecification.class, arazzo);
    }

    private ArazzoSpecification loadArazzoFromPath(final String pathOfArazzo) {
        // TODO handle options from external source

        Parser parser = new Parser();
        ParseOptions parseOptions = ParseOptions.ofDefault();
        var parseResult = parser.readLocation(pathOfArazzo, parseOptions);
        if (!parseResult.getMessages().isEmpty()) {
            log.info("Parsing report of source '%s':%n%s".formatted(pathOfArazzo, String.join("\n", parseResult.getMessages())));
        }
        if (parseResult.isInvalid()) throw new ItarazzoIllegalStateException("Parsing failed");

        // initializes arazzo/oas referenced through source descriptions
        SourceDescriptionInitializer.initialize(parseResult.getArazzo());

        ValidatorRegistry validatorRegistry = new ValidatorRegistry();
        ValidationOptions validationOptions = ValidationOptions.ofDefault();
        var validationResult = validatorRegistry.validate(parseResult.getArazzo(), validationOptions);
        if (!validationResult.getMessages().isEmpty()) {
            log.info("Validation report of source '%s':%n%s".formatted(pathOfArazzo, String.join("\n", validationResult.getMessages())));
        }
        if (validationResult.isInvalid()) throw new ItarazzoIllegalStateException("Validation failed");

        return validationResult.getArazzo();
    }
}
