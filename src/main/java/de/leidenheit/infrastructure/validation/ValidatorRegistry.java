package de.leidenheit.infrastructure.validation;

import com.google.common.base.Strings;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.infrastructure.validation.validators.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ValidatorRegistry {

    private static final String LOCATION = "arazzoSpec";
    private final List<Validator<?>> validators = new ArrayList<>(
            // register default validators
            List.of(
                    new InfoValidator(),
                    new SourceDescriptionValidator(),
                    new WorkflowValidator(),
                    new StepValidator(),
                    new ParameterValidator(),
                    new SuccessActionValidator(),
                    new FailureActionValidator(),
                    new CriterionValidator(),
                    new CriterionExpressionTypeObjectValidator(),
                    new RequestBodyValidator(),
                    new PayloadReplacementObjectValidator(),
                    new ReusableObjectValidator(),
                    new ComponentsValidator()
            ));

    public void register(final Validator<?> validator) {
        validators.add(validator);
    }

    public ValidationResult validate(final ArazzoSpecification arazzo, final ValidationOptions options) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(arazzo.getArazzo())) {
            result.addError(LOCATION, "arazzo is mandatory");
        } else if (!isSemanticVersioningFormat(arazzo.getArazzo())) {
            result.addWarning(LOCATION, "arazzo does not adhere to semantic versioning");
        }

        // info
        result.merge(validateObject(arazzo.getInfo(), null, arazzo, options));

        // sourceDescriptions
        arazzo.getSourceDescriptions().forEach(sourceDescription ->
                result.merge(validateObject(sourceDescription, null, arazzo, options))
        );

        // workflows
        arazzo.getWorkflows().forEach(workflow ->
                result.merge(validateObject(workflow, null, arazzo, options))
        );

        // components
        if (Objects.nonNull(arazzo.getComponents())) {
            result.merge(validateObject(arazzo.getComponents(), null, arazzo, options));
        }

        // extensions
        if (Objects.nonNull(arazzo.getExtensions()) && !arazzo.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(arazzo.getExtensions(), null, arazzo, options));
        }

        result.setArazzo(arazzo);

        return result;
    }

    @SuppressWarnings("unchecked")
    private <T, C> ValidationResult validateObject(
            final T partOfArazzo,
            final C context,
            final ArazzoSpecification arazzo,
            final ValidationOptions options) {
        Validator<T> validator = (Validator<T>) findValidatorForObject(partOfArazzo);
        if (Objects.isNull(validator)) throw new ItarazzoIllegalStateException(
                "No validator found for class: %s".formatted(partOfArazzo.getClass()));
        return validator.validate(partOfArazzo, context, arazzo, options);
    }

    private <T> Validator<?> findValidatorForObject(final T partOfArazzo) {
        for (Validator<?> validator : validators) {
            if (validator.supports(partOfArazzo.getClass())) {
                return validator;
            }
        }
        return null;
    }

    private boolean isSemanticVersioningFormat(final String version) {
        return version.matches("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    }
}
