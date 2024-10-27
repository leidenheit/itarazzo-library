package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.ReusableObject;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class ReusableObjectValidator implements Validator<ReusableObject> {

    public static final String LOCATION = "reusableObject";

    @Override
    public <C> ValidationResult validate(final ReusableObject reusableObject,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Objects.isNull(reusableObject.getReference())) result.addError(LOCATION, "reference: is mandatory");
        if (!Strings.isNullOrEmpty(reusableObject.getValue())) {
            if (reusableObject.getReference() instanceof String referenceAsString) {
                if (referenceAsString.startsWith("$") && !referenceAsString.contains(".parameters.")) {
                    result.addError(LOCATION, "value: is applicable for parameter object references only");
                }
            }
        }
        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return ReusableObject.class.isAssignableFrom(clazz);
    }
}
