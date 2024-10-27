package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.SuccessAction;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.validation.ValidationOptions;
import de.leidenheit.infrastructure.validation.ValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import java.util.Objects;

public class SuccessActionValidator implements Validator<SuccessAction> {

    private static final String LOCATION = "successAction";

    @Override
    public <C> ValidationResult validate(final SuccessAction successAction,
                                         final C context,
                                         final ArazzoSpecification arazzo,
                                         final ValidationOptions validationOptions) {
        var result = ValidationResult.builder().build();

        if (Strings.isNullOrEmpty(successAction.getName())) result.addError(LOCATION, "name: is mandatory");
        if (Objects.isNull(successAction.getType())) result.addError(LOCATION, "type: is mandatory");

        if (SuccessAction.SuccessActionType.GOTO.equals(successAction.getType())) {
            int countSet = 0;
            if (successAction.getWorkflowId() != null) countSet++;
            if (successAction.getStepId() != null) countSet++;

            if (countSet == 0) {
                result.addError(LOCATION, "goto: requires one of 'workflowId' or 'stepId'");
            } else if (countSet > 1) {
                result.addError(LOCATION, "goto: mutually excludes 'workflowId' and 'stepId'");
            }

            if (Objects.nonNull(successAction.getWorkflowId())) {
                var workflowExists = arazzo.getWorkflows().stream()
                        .anyMatch(wf -> wf.getWorkflowId().equals(successAction.getWorkflowId()));
                if (!workflowExists) {
                    result.addError(LOCATION, "workflow: '%s' not found".formatted(successAction.getWorkflowId()));
                }
            }

            if (Objects.nonNull(successAction.getStepId()) && (context instanceof Step stepContext)) {
                var parentWorkflow = findParentWorkflow(stepContext, arazzo);
                if (Objects.nonNull(parentWorkflow)) {
                    var stepExists = parentWorkflow.getSteps().stream()
                            .anyMatch(step -> step.getStepId().equals(successAction.getStepId()));
                    if (!stepExists) {
                        result.addError(LOCATION, "step: '%s' not found".formatted(successAction.getStepId()));
                    }
                } else {
                    result.addError(LOCATION, "workflow: not found for step in context %s".formatted(successAction.getStepId()));
                }
            }
        } else if (SuccessAction.SuccessActionType.END.equals(successAction.getType())) {
            if (Objects.nonNull(successAction.getWorkflowId()) || Objects.nonNull(successAction.getStepId())) {
                result.addError(LOCATION, "end: must not have 'workflowId' nor 'stepId'");
            }
        }

        if (Objects.nonNull(successAction.getCriteria())) {
            successAction.getCriteria().forEach(criterion -> {
                var criterionValidator = new CriterionValidator();
                result.merge(criterionValidator.validate(criterion, successAction, arazzo, validationOptions));
            });
        }

        if (Objects.nonNull(successAction.getExtensions()) && !successAction.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(successAction.getExtensions(), successAction, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return SuccessAction.class.isAssignableFrom(clazz);
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
}
