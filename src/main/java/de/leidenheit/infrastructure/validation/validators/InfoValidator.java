package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Info;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class InfoValidator implements Validator<Info> {

    private static final String LOCATION = "info";

    @Override
    public <C> ValidationResult validate(final Info info,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(info.getTitle())) result.addError(LOCATION, "title: is mandatory");

        if (Strings.isNullOrEmpty(info.getVersion())) {
            result.addError(LOCATION, "version: is mandatory");
        } else if (!isSemanticVersioningFormat(info.getVersion())) {
            result.addWarning(LOCATION, "version: does not adhere to semantic versioning");
        }

        if (Objects.nonNull(info.getExtensions()) && !info.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(info.getExtensions(), info, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return Info.class.isAssignableFrom(clazz);
    }

    private boolean isSemanticVersioningFormat(final String version) {
        return version.matches("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    }
}
