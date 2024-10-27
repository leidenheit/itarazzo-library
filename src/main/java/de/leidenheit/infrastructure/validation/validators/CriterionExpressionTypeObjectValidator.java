package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.CriterionExpressionTypeObject;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.List;
import java.util.Objects;

public class CriterionExpressionTypeObjectValidator implements Validator<CriterionExpressionTypeObject> {

    private static final String LOCATION = "criterionExpressionTypeObject";

    @Override
    public <C> ValidationResult validate(
            final CriterionExpressionTypeObject criterionExpressionTypeObject,
            final C context,
            final ArazzoSpecification arazzo,
            final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Objects.isNull(criterionExpressionTypeObject.getType())) result.addError(LOCATION, "type: is mandatory");
        if (Strings.isNullOrEmpty(criterionExpressionTypeObject.getVersion())) result.addError(LOCATION, "version: is mandatory");

        if (CriterionExpressionTypeObject.CriterionExpressionType.JSONPATH.equals(criterionExpressionTypeObject.getType())) {
            if (!"draft-goessner-dispatch-jsonpath-00".equals(criterionExpressionTypeObject.getVersion())) {
                result.addError(LOCATION, "version: invalid for jsonpath");
            }
        } else if (CriterionExpressionTypeObject.CriterionExpressionType.XPATH.equals(criterionExpressionTypeObject.getType())) {
            List<String> allowedVersions = List.of("xpath-30", "xpath-20", "xpath-10");
            if (!allowedVersions.contains(criterionExpressionTypeObject.getVersion())) {
                result.addError(LOCATION, "version: invalid for xpath");
            }
        }

        if (Objects.nonNull(criterionExpressionTypeObject.getExtensions()) && !criterionExpressionTypeObject.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(criterionExpressionTypeObject.getExtensions(), criterionExpressionTypeObject, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return CriterionExpressionTypeObject.class.isAssignableFrom(clazz);
    }
}
