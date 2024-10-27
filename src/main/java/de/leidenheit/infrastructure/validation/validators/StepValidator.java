package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.utils.JsonPointerOperationComparator;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class StepValidator implements Validator<Step> {

    private static final String LOCATION = "step";

    @Override
    public <C> ValidationResult validate(final Step step,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(step.getStepId())) {
            result.addError(LOCATION, "stepId: is mandatory");
        } else if (!isRecommendedStepIdFormat(step.getStepId())) {
            result.addWarning(LOCATION, "stepId: does not comply to [A-Za-z0-9_\\-]+: %s".formatted(step.getStepId()));
        }
        Workflow parentWorkflow = findParentWorkflow(step, arazzo);
        if (Objects.nonNull(parentWorkflow)) {
            boolean uniqueStepId = parentWorkflow.getSteps().stream()
                    .filter(s -> s.getStepId().equals(step.getStepId()))
                    .count() == 1;
            if (!uniqueStepId)
                result.addError(LOCATION, "stepId: '%s' must be unique within workflow %s".formatted(step.getStepId(), parentWorkflow.getWorkflowId()));
        } else {
            result.addError(LOCATION, "stepId: '%s' has no parent workflow".formatted(step.getStepId()));
        }

        int countSet = 0;
        if (!Strings.isNullOrEmpty(step.getOperationId())) countSet++;
        if (!Strings.isNullOrEmpty(step.getOperationPath())) countSet++;
        if (!Strings.isNullOrEmpty(step.getWorkflowId())) countSet++;
        if (countSet == 0) {
            result.addError("", "step %s requires one of 'operationId', 'operationPath' or 'workflowId'".formatted(step.getStepId()));
        } else if (countSet > 1) {
            result.addError("", "step %s fields 'operationId', 'operationPath' and 'workflowId' are mutually exclusive".formatted(step.getStepId()));
        }

        if (!Strings.isNullOrEmpty(step.getOperationId())) {
            boolean operationExists = validateOperationId(step.getOperationId(), arazzo, validationOptions);
            if (!operationExists) {
                result.addError(LOCATION, "operationId: was not found for step %s".formatted(step.getStepId()));
            }
        }
        if (!Strings.isNullOrEmpty(step.getOperationPath())) {
            boolean validOperationPath = validateOperationPath(step.getOperationPath(), arazzo, validationOptions);
            if (!validOperationPath) {
                result.addError(LOCATION, "operationPath: was not found for step %s".formatted(step.getStepId()));
            }
        }
        if (!Strings.isNullOrEmpty(step.getWorkflowId())) {
            boolean workflowExists = validateWorkflowId(step.getWorkflowId(), arazzo, validationOptions);
            if (!workflowExists) {
                result.addError(LOCATION, "workflowId: was not found for step %s".formatted(step.getStepId()));
            }
        }

        if (Objects.nonNull(step.getRequestBody())) {
            var requestBodyValidator = new RequestBodyValidator();
            result.merge(requestBodyValidator.validate(step.getRequestBody(), step, arazzo, validationOptions));
        }

        if (Objects.nonNull(step.getSuccessCriteria())) {
            step.getSuccessCriteria().forEach(criterion -> {
                var criterionValidator = new CriterionValidator();
                result.merge(criterionValidator.validate(criterion, step, arazzo, validationOptions));
            });
        }

        if (Objects.nonNull(step.getOnSuccess())) {
            var containsDuplicates = step.getOnSuccess().stream().distinct().count() != step.getOnSuccess().size();
            if (containsDuplicates) result.addError(LOCATION, "onSuccess must not contain duplicates");

            var successActionValidator = new SuccessActionValidator();
            step.getOnSuccess().forEach(successAction ->
                    result.merge(successActionValidator.validate(successAction, step, arazzo, validationOptions)));
        }

        if (Objects.nonNull(step.getOnFailure())) {
            var containsDuplicates = step.getOnFailure().stream().distinct().count() != step.getOnFailure().size();
            if (containsDuplicates) result.addError(LOCATION, "onFailure must not contain duplicates");

            var failureActionValidator = new FailureActionValidator();
            step.getOnFailure().forEach(failureAction ->
                    result.merge(failureActionValidator.validate(failureAction, step, arazzo, validationOptions)));
        }

        if (Objects.nonNull(step.getOutputs())) {
            var validKeyFormat = step.getOutputs().keySet().stream().allMatch(this::isValidKeyFormat);
            if (!validKeyFormat) result.addError("", "output keys of step %s must comply to ^[a-zA-Z0-9\\.\\-_]+$"
                    .formatted(step.getStepId()));
        }

        if (Objects.nonNull(step.getParameters())) {
            var containsDuplicates = step.getParameters().stream().distinct().count() != step.getParameters().size();
            if (containsDuplicates) result.addError(LOCATION, "parameters: must not contain duplicates");

            var parameterValidator = new ParameterValidator();
            step.getParameters().forEach(parameter -> result.merge(
                    parameterValidator.validate(parameter, step, arazzo, validationOptions)));
        }

        if (Objects.nonNull(step.getExtensions()) && !step.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(step.getExtensions(), step, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return Step.class.isAssignableFrom(clazz);
    }

    private boolean isRecommendedStepIdFormat(final String workflowId) {
        return workflowId.matches("^[A-Za-z0-9_\\\\-]+$");
    }

    private boolean isValidKeyFormat(final String key) {
        return key.matches("^[a-zA-Z0-9.\\-_]+$");
    }

    private Workflow findParentWorkflow(final Step step,
                                        final ArazzoSpecification arazzo) {
        for (Workflow workflow : arazzo.getWorkflows()) {
            if (workflow.getSteps().contains(step)) {
                return workflow;
            }
        }
        return null;
    }

    private boolean validateOperationId(final String operationId,
                                        final ArazzoSpecification arazzo,
                                        final ValidationOptions validationOptions) {
        for (SourceDescription sourceDescription : arazzo.getSourceDescriptions()) {
            if (SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType())) {
                var refOas = sourceDescription.getReferencedOpenAPI();
                var operationExists = refOas.getPaths().values().stream()
                        .anyMatch(pathItem ->
                                pathItem.readOperations().stream()
                                        .anyMatch(operation -> operationId.contains(operation.getOperationId())));
                if (operationExists) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean validateOperationPath(final String operationPath,
                                          final ArazzoSpecification arazzo,
                                          final ValidationOptions validationOptions) {
        for (SourceDescription sourceDescription : arazzo.getSourceDescriptions()) {
            if (!operationPath.contains(sourceDescription.getName())) continue;
            if (!SourceDescription.SourceDescriptionType.OPENAPI.equals(sourceDescription.getType())) continue;

            var refOas = sourceDescription.getReferencedOpenAPI();
            if (Objects.nonNull(refOas)) {
                return JsonPointerOperationComparator.compareJsonPointerToPathAndOperation(
                        operationPath, refOas.getPaths()
                );
            }
        }
        return false;
    }

    private boolean validateWorkflowId(final String workflowId,
                                       final ArazzoSpecification arazzo,
                                       final ValidationOptions validationOptions) {
        if (workflowId.startsWith("$sourceDescriptions.")) {
            return arazzo.getSourceDescriptions().stream()
                    .filter(s -> SourceDescription.SourceDescriptionType.ARAZZO.equals(s.getType()))
                    .anyMatch(s -> s.getReferencedArazzo().getWorkflows().stream().anyMatch(wf -> workflowId.contains(wf.getWorkflowId())));
        }

        return arazzo.getWorkflows().stream()
                .anyMatch(wf -> wf.getWorkflowId().equals(workflowId));
    }
}
