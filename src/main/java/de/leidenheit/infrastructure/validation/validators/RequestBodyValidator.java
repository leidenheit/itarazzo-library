package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.RequestBody;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class RequestBodyValidator implements Validator<RequestBody> {

    public static final String LOCATION = "requestBody";

    @Override
    public <C> ValidationResult validate(final RequestBody requestBody,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(requestBody.getContentType())) result.addWarning(LOCATION, "contentType: is not defined");
        if (Objects.isNull(requestBody.getPayload())) result.addError(LOCATION, "payload: is mandatory");

        if (Objects.nonNull(requestBody.getReplacements())) {
            var payloadReplacementObjectValidator = new PayloadReplacementObjectValidator();
            requestBody.getReplacements().forEach(payloadReplacementObject ->
                    result.merge(payloadReplacementObjectValidator.validate(
                            payloadReplacementObject, requestBody, arazzo, validationOptions)));
        }

        if (Objects.nonNull(requestBody.getExtensions()) && !requestBody.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(requestBody.getExtensions(), requestBody, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return RequestBody.class.isAssignableFrom(clazz);
    }
}
