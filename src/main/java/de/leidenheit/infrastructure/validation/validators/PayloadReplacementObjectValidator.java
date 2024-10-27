package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.PayloadReplacementObject;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class PayloadReplacementObjectValidator implements Validator<PayloadReplacementObject> {

    public static final String LOCATION = "payloadReplacementObject";

    @Override
    public <C> ValidationResult validate(final PayloadReplacementObject payloadReplacementObject,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(payloadReplacementObject.getTarget())) result.addError(LOCATION, "target: is mandatory");
        if (Objects.isNull(payloadReplacementObject.getValue())) result.addError(LOCATION, "value: is mandatory");

        if (Objects.nonNull(payloadReplacementObject.getExtensions()) && !payloadReplacementObject.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(payloadReplacementObject.getExtensions(), payloadReplacementObject, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return PayloadReplacementObject.class.isAssignableFrom(clazz);
    }
}
