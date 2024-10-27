package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;
import de.leidenheit.infrastructure.utils.IOUtils;

import java.util.Objects;

public class SourceDescriptionValidator implements Validator<SourceDescription> {

    private static final String LOCATION = "sourceDescription";

    @Override
    public <C> ValidationResult validate(final SourceDescription sourceDescription,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(sourceDescription.getName())) {
            result.addError(LOCATION, "name: is mandatory");
        } else if (!isRecommendedNameFormat(sourceDescription.getName())) {
            result.addWarning(LOCATION, "name: does not comply to [A-Za-z0-9_\\-]+: %s".formatted(sourceDescription.getName()));
        }

        if (!Strings.isNullOrEmpty(sourceDescription.getUrl())) {
            var validUrl = IOUtils.isValidFileOrUrl(sourceDescription.getUrl());
            if (!validUrl) {
                result.addError(LOCATION, "url: '%s' must be available and valid URI reference as per RFC3986".formatted(sourceDescription.getUrl()));
            }
        } else {
            result.addError(LOCATION, "url: is mandatory");
        }

        if (SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType())) {
            if (Objects.isNull(sourceDescription.getReferencedOpenAPI()))
                result.addError("", "expecting source description '%s' referenced OAS to be set but was not"
                        .formatted(sourceDescription.getName()));
        } else if (SourceDescription.SourceDescriptionType.ARAZZO.equals(sourceDescription.getType())) {
            if (Objects.isNull(sourceDescription.getReferencedArazzo()))
                result.addError("", "expecting source description '%s' referenced Arazzo to be set but was not"
                        .formatted(sourceDescription.getName()));
        }

        if (Objects.nonNull(sourceDescription.getExtensions()) && !sourceDescription.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(sourceDescription.getExtensions(), sourceDescription, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return SourceDescription.class.isAssignableFrom(clazz);
    }

    private boolean isRecommendedNameFormat(final String name) {
        return name.matches("^[A-Za-z0-9_\\-]+$");
    }
}
