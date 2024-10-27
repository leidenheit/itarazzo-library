package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Parameter;
import de.leidenheit.core.model.Step;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class ParameterValidator implements Validator<Parameter> {

    private static final String LOCATION = "parameter";

    @Override
    public <C> ValidationResult validate(final Parameter parameter,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        if (Objects.isNull(context)) throw new ItarazzoIllegalStateException(
                "Validation context for parameter must not be null");

        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(parameter.getName())) result.addError(LOCATION, "name: is mandatory");
        if (Objects.isNull(parameter.getValue())) result.addError(LOCATION, "value: is mandatory");

        if (context instanceof Step stepContext
                && Objects.isNull(parameter.getIn())
                && Objects.nonNull(stepContext.getWorkflowId())) {
                result.addError(LOCATION, "in: is mandatory when step in context defines a workflowId");
        }

        if (Objects.nonNull(parameter.getExtensions()) && !parameter.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(parameter.getExtensions(), parameter, arazzo, validationOptions));
        }
        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return Parameter.class.isAssignableFrom(clazz);
    }
}
