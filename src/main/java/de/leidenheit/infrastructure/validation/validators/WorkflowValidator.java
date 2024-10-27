package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class WorkflowValidator implements Validator<Workflow> {

    private static final String LOCATION = "workflow";

    @Override
    public <C> ValidationResult validate(final Workflow workflow,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(workflow.getWorkflowId())) {
            result.addError(LOCATION, "workflowId: is mandatory");
        } else if (!isRecommendedWorkflowIdFormat(workflow.getWorkflowId())) {
            result.addWarning(LOCATION, "workflowId: '%s' does not comply to [A-Za-z0-9_\\-]+.".formatted(workflow.getWorkflowId()));
        }
        boolean uniqueWorkflowId = arazzo.getWorkflows().stream()
                .filter(wf -> wf.getWorkflowId().equals(workflow.getWorkflowId()))
                .count() == 1;
        if (!uniqueWorkflowId) result.addError(LOCATION, "workflowId: must be unique");

        if (Objects.nonNull(workflow.getDependsOn())) {
            workflow.getDependsOn().forEach(workflowIdThatMustBeCompletedFirst -> {
                var isRuntimeExpression = workflowIdThatMustBeCompletedFirst.startsWith("$sourceDescriptions.");
                if (!isRuntimeExpression) {
                    var exists = arazzo.getWorkflows().stream().anyMatch(wf -> wf.getWorkflowId().equals(workflowIdThatMustBeCompletedFirst));
                    if (!exists)
                        result.addError(LOCATION, "dependsOn: referenced workflow not found: '%s'".formatted(workflowIdThatMustBeCompletedFirst));
                }
                // references are ignored here and will be handled by a designated reference validator
            });
        }

        if (Objects.isNull(workflow.getSteps()) || workflow.getSteps().isEmpty()) {
            result.addError(LOCATION, "steps: at least one step must exist");
        } else {
            var stepValidator = new StepValidator();
            workflow.getSteps().forEach(step ->
                    result.merge(stepValidator.validate(step, workflow, arazzo, validationOptions)));
        }

        if (Objects.nonNull(workflow.getSuccessActions())) {
            var containsDuplicates = workflow.getSuccessActions().stream().distinct().count() != workflow.getSuccessActions().size();
            if (containsDuplicates) result.addError(LOCATION, "successActions: must not contain duplicates");

            var successActionValidator = new SuccessActionValidator();
            workflow.getSuccessActions().forEach(successAction ->
                    result.merge(successActionValidator.validate(successAction, workflow, arazzo, validationOptions)));
        }

        if (Objects.nonNull(workflow.getFailureActions())) {
            var containsDuplicates = workflow.getFailureActions().stream().distinct().count() != workflow.getFailureActions().size();
            if (containsDuplicates) result.addError(LOCATION, "failureActions: must not contain duplicates");

            var failureActionValidator = new FailureActionValidator();
            workflow.getFailureActions().forEach(failureAction ->
                    result.merge(failureActionValidator.validate(failureAction, workflow, arazzo, validationOptions)));
        }

        if (Objects.nonNull(workflow.getOutputs())) {
            var validKeyFormat = workflow.getOutputs().keySet().stream().allMatch(this::isValidKeyFormat);
            if (!validKeyFormat) result.addError("", "Workflow keys of 'outputs' must comply to ^[a-zA-Z0-9\\.\\-_]+$");
        }

        if (Objects.nonNull(workflow.getParameters())) {
            var containsDuplicates = workflow.getParameters().stream().distinct().count() != workflow.getParameters().size();
            if (containsDuplicates) result.addError(LOCATION, "parameters: must not contain duplicates");

            var parameterValidator = new ParameterValidator();
            workflow.getParameters().forEach(parameter -> result.merge(
                    parameterValidator.validate(parameter, workflow, arazzo, validationOptions)));
        }

        if (Objects.nonNull(workflow.getExtensions()) && !workflow.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(workflow.getExtensions(), workflow, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return Workflow.class.isAssignableFrom(clazz);
    }

    private boolean isRecommendedWorkflowIdFormat(final String workflowId) {
        return workflowId.matches("^[A-Za-z0-9_\\\\-]+$");
    }

    private boolean isValidKeyFormat(final String key) {
        return key.matches("^[a-zA-Z0-9.\\-_]+$");
    }
}
