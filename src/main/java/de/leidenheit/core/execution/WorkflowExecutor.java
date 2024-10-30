package de.leidenheit.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.exception.ItarazzoInterruptException;
import de.leidenheit.core.exception.ItarazzoUnsupportedException;
import de.leidenheit.core.execution.context.ExecutionResultContext;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.FailureAction;
import de.leidenheit.core.model.SourceDescription;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.SuccessAction;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.evaluation.CriterionEvaluator;
import de.leidenheit.infrastructure.resolving.SpecExpressionResolver;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class WorkflowExecutor {

    private final ArazzoSpecification arazzo;
    private final Map<String, Object> inputs;
    private final SpecExpressionResolver resolver;
    private final StepExecutor stepExecutor;

    public WorkflowExecutor(final ArazzoSpecification arazzo, final Map<String, Object> inputs) {
        this.arazzo = arazzo;
        this.inputs = inputs;
        this.resolver = new SpecExpressionResolver(arazzo, inputs);

        var criterionEvaluator = new CriterionEvaluator(resolver, new ObjectMapper());
        this.stepExecutor = new RestAssuredStepExecutor(arazzo, criterionEvaluator, resolver); // TODO as dynamic factory
    }

    public void executeWorkflow(final Workflow workflow) {
        log.info("Executing workflow '{}' with inputs: {}", workflow.getWorkflowId(), inputs.toString());

        Map<String, Integer> retryCounters = new HashMap<>();
        int currentStepIndex = 0;
        while (currentStepIndex < workflow.getSteps().size()) {
            Step currentStep = workflow.getSteps().get(currentStepIndex);

            // execute referenced workflow as content of this step
            if (Objects.nonNull(currentStep.getWorkflowId())) {
                executeToReferencedWorkflow(arazzo, currentStep, inputs);
                currentStepIndex++;
            } else {
                // execute step content
                log.info("Running step '{}'", currentStep.getStepId());
                var executionResult = stepExecutor.executeStep(workflow, currentStep);
                ExecutionDecision executionDecision = handleExecutionResultActions(arazzo, workflow, currentStep, executionResult, inputs, retryCounters, resolver);
                log.info("Finished step '{}' successfully: {}", currentStep.getStepId(), executionResult.isSuccessful());

                if (executionDecision.isMustEnd()) break;

                if (Objects.isNull(executionDecision.nextStepIndex)) {
                    // no specific reference, so choose sequentially the next step
                    currentStepIndex++;
                } else {
                    // we got a reference, so apply it
                    currentStepIndex = executionDecision.getNextStepIndex();
                }

            }
        }
        var workflowOutputs = handleOutputs(workflow, resolver);
        log.info("Finished workflow '{}': outputs={}", workflow.getWorkflowId(), workflowOutputs);
    }

    private ExecutionDecision handleExecutionResultActions(final ArazzoSpecification arazzo,
                                                           final Workflow workflow,
                                                           final Step currentStep,
                                                           final ExecutionResultContext executionResultContext,
                                                           final Map<String, Object> inputs,
                                                           final Map<String, Integer> retryCounters,
                                                           final SpecExpressionResolver resolver) {
        if (executionResultContext.isSuccessful()) {
            var successActions = collectSuccessActions(workflow, executionResultContext);
            return handleSuccessActions(arazzo, successActions, workflow);
        } else {
            var failureActions = collectFailureActions(workflow, executionResultContext);

            if (failureActions.isEmpty()) {
                log.error("Unexpected empty set of failure actions for unsuccessful step '{}' in workflow '{}'",
                        workflow.getWorkflowId(), currentStep.getStepId());
                throw new ItarazzoInterruptException("No handling for unsuccessful operation");
            }

            return handleFailureActions(arazzo, failureActions, currentStep, workflow, retryCounters, inputs, resolver);
        }
    }

    private ExecutionDecision handleGotoStepAction(final String stepId, final Workflow workflow) {
        var index = findStepIndexById(workflow, stepId);
        return ExecutionDecision.builder().nextStepIndex(index).mustEnd(false).build();
    }

    private ExecutionDecision handleGotoWorkflowAction(final ArazzoSpecification arazzo,
                                                       final String workflowId) {
        handleWorkflowIdExecutionReference(arazzo, workflowId);
        // one-way to another workflow will end the current workflow execution
        return ExecutionDecision.builder().mustEnd(true).build();
    }

    private ExecutionDecision handleEndAction() {
        return ExecutionDecision.builder().mustEnd(true).build();
    }

    private ExecutionDecision handleRetryAction(final Workflow workflow, final String retryStepId, final Long retryAfter) {
        doWait(retryAfter);
        return ExecutionDecision.builder()
                .nextStepIndex(findStepIndexById(workflow, retryStepId))
                .mustEnd(false)
                .build();
    }

    private ExecutionDecision handleSuccessActions(final ArazzoSpecification arazzo,
                                                   final List<SuccessAction> actionList,
                                                   final Workflow workflow) {
        for (SuccessAction successAction : actionList) {
            switch (successAction.getType()) {
                case GOTO -> {
                    // find referenced step or workflow to execute
                    if (Objects.nonNull(successAction.getStepId())) {
                        log.info("Triggered success action '{}' as {}: interrupts sequential execution and moves to step '{}'",
                                successAction.getName(), successAction.getType(), successAction.getStepId());
                        return handleGotoStepAction(successAction.getStepId(), workflow);
                    } else if (Objects.nonNull(successAction.getWorkflowId())) {
                        log.info("Triggered success action '{}' as {}: interrupts sequential execution and moves to workflow '{}'",
                                successAction.getName(), successAction.getType(), successAction.getWorkflowId());

                        return handleGotoWorkflowAction(arazzo, successAction.getWorkflowId());
                    }
                }
                case END -> {
                    log.info("Triggered success action '{}' as {}: ends workflow", successAction.getName(), successAction.getType());
                    return handleEndAction();
                }
                default -> {
                    log.error("Success action '{}' of type '{}' is not handled due to missing implementation",
                            successAction.getName(), successAction.getType());
                    throw new ItarazzoUnsupportedException("SuccessActionType: '%s'".formatted(successAction.getType()));
                }
            }
        }
        // stick to sequential execution due to no success actions
        return ExecutionDecision.builder().mustEnd(false).build();
    }

    private ExecutionDecision handleFailureActions(final ArazzoSpecification arazzo,
                                                   final List<FailureAction> actionList,
                                                   final Step currentStep,
                                                   final Workflow workflow,
                                                   final Map<String, Integer> retryCounters,
                                                   final Map<String, Object> inputs,
                                                   final SpecExpressionResolver resolver) {
        for (FailureAction failureAction : actionList) {
            switch (failureAction.getType()) {
                case GOTO -> {
                    // find referenced step or workflow to execute
                    if (Objects.nonNull(failureAction.getStepId())) {
                        log.info("Triggered failure action '{}' as {}: interrupts sequential execution and moves to step '{}'",
                                failureAction.getName(), failureAction.getType(), failureAction.getStepId());
                        return handleGotoStepAction(failureAction.getStepId(), workflow);
                    } else if (Objects.nonNull(failureAction.getWorkflowId())) {
                        log.info("Triggered failure action '{}' as {}: interrupts sequential execution and moves to workflow '{}'",
                                failureAction.getName(), failureAction.getType(), failureAction.getWorkflowId());
                        return handleGotoWorkflowAction(arazzo, failureAction.getWorkflowId());
                    }
                }
                case END -> {
                    log.info("Triggered failure action '{}' as {}: ends workflow", failureAction.getName(), failureAction.getType());
                    return handleEndAction();
                }
                case RETRY -> {
                    int retryCount = retryCounters.getOrDefault(currentStep.getStepId(), 0);
                    if (retryCount >= failureAction.getRetryLimit()) {
                        var msg = "Reached retry limit failure action '%s'".formatted(failureAction.getName());
                        log.error(msg);
                        throw new ItarazzoInterruptException(msg);
                    }
                    retryCount++;
                    retryCounters.put(currentStep.getStepId(), retryCount);
                    log.info("Triggered failure action '{}' as {}: retrying {}/{} after waiting {} seconds",
                            failureAction.getName(),
                            failureAction.getType(),
                            retryCount,
                            failureAction.getRetryLimit(),
                            failureAction.getRetryAfter().longValue());

                    // execute actions defined to run before any retry attempt
                    if (Objects.nonNull(failureAction.getStepId())) {
                        var refResult = handleStepIdExecutionReference(arazzo, workflow, failureAction.getStepId(), inputs, resolver);
                        if (Objects.nonNull(refResult)) {
                            if (refResult.mustEnd) {
                                log.warn("Referenced step from retry action was executed but unexpectedly wants to end the workflow before any retry attempt: stepId={} executionDecisionResult={}",
                                        failureAction.getStepId(), refResult);
                            } else if (Objects.nonNull(refResult.nextStepIndex)) {
                                log.warn("Referenced step from retry action was executed but unexpectedly wants to interrupt the current execution order: stepId={} executionDecisionResult={}",
                                        failureAction.getStepId(), refResult);
                            }
                            log.warn("Failure action reference execution result will be ignored for type RETRY");
                        }
                    } else if (Objects.nonNull(failureAction.getWorkflowId())) {
                        handleWorkflowIdExecutionReference(arazzo, failureAction.getWorkflowId());
                    }

                    // retry the current step
                    var retryAfter = failureAction.getRetryAfter().longValue();
                    return handleRetryAction(workflow, currentStep.getStepId(), retryAfter);
                }
                default -> {
                    log.error("Failure action '{}' of type '{}' is not handled due to missing implementation",
                            failureAction.getName(), failureAction.getType());
                    throw new ItarazzoUnsupportedException("FailureActionType: '%s'".formatted(failureAction.getType()));
                }
            }
        }
        // stick to sequential execution due to no failure actions
        return ExecutionDecision.builder().mustEnd(false).build();
    }

    private Map<String, Object> handleOutputs(final Workflow workflow, final SpecExpressionResolver resolver) {
        var resolvedOutputs = new HashMap<String, Object>();
        if (Objects.isNull(workflow.getOutputs())) return resolvedOutputs;

        workflow.getOutputs().forEach((key, value) -> {
            Object resolvedOutput;
            if (value instanceof TextNode textNode) {
                resolvedOutput = resolver.resolveString(textNode.asText());
            } else {
                resolvedOutput = resolver.resolveString(value.toString());
            }

            if (Objects.isNull(resolvedOutput)) {
                log.error("Expected output to be successfully resolved but was not: key={} value={}", key, value);
                throw new ItarazzoIllegalStateException("Must not be null");
            }

            resolvedOutputs.put(key, resolvedOutput);
        });

        addResolvedOutputs(workflow.getWorkflowId(), resolvedOutputs);
        return resolvedOutputs;
    }

    private ExecutionDecision handleStepIdExecutionReference(final ArazzoSpecification arazzo,
                                                             final Workflow workflow,
                                                             final String referencedStepId,
                                                             final Map<String, Object> inputs,
                                                             final SpecExpressionResolver resolver) {
        var refStep = workflow.getSteps().stream()
                .filter(step -> referencedStepId.contains(step.getStepId()))
                .findFirst()
                .orElseThrow(() -> {
                    var msg = "Step not found: stepId='%s'".formatted(referencedStepId);
                    log.error(msg);
                    return new ItarazzoIllegalStateException(msg);
                });
        var executionResult = stepExecutor.executeStep(workflow, refStep);
        return handleExecutionResultActions(arazzo, workflow, refStep, executionResult, inputs, null, resolver);
    }

    private void handleWorkflowIdExecutionReference(final ArazzoSpecification arazzo,
                                                    final String referencedWorkflowId) {
        var workflowToTransferTo = findWorkflowByWorkflowId(arazzo, referencedWorkflowId);
        executeWorkflow(workflowToTransferTo);
    }

    private SourceDescription findRelevantSourceDescriptionByReferencedWorkflowId(final ArazzoSpecification arazzo,
                                                                                  final String referencedWorkflowId) {
        var sourceDescription = arazzo.getSourceDescriptions().get(0);
        if (arazzo.getSourceDescriptions().size() > 1) {
            sourceDescription = arazzo.getSourceDescriptions().stream()
                    .filter(s -> referencedWorkflowId.contains(s.getName()))
                    .findFirst()
                    .orElseThrow(() -> {
                        var msg = "Workflow not found: workflowId='%s'".formatted(referencedWorkflowId);
                        log.error(msg);
                        return new ItarazzoIllegalStateException(msg);
                    });
        }
        return sourceDescription;
    }

    private Workflow findWorkflowByWorkflowId(final ArazzoSpecification arazzo, final String workflowId) {
        return arazzo.getWorkflows().stream()
                .filter(wf -> workflowId.contains(wf.getWorkflowId()))
                .findFirst()
                .orElseThrow(() -> {
                    var msg = "Workflow not found: workflowId='%s'".formatted(workflowId);
                    log.error(msg);
                    return new ItarazzoIllegalStateException(msg);
                });
    }

    private int findStepIndexById(final Workflow workflow, final String stepId) {
        for (int i = 0; i < workflow.getSteps().size(); i++) {
            if (workflow.getSteps().get(i).getStepId().equals(stepId)) {
                return i;
            }
        }
        var msg = "Step not found: stepId='%s'".formatted(stepId);
        log.error(msg);
        throw new ItarazzoIllegalStateException(msg);
    }

    private List<SuccessAction> collectSuccessActions(final Workflow workflow, final ExecutionResultContext executionResultContext) {
        List<SuccessAction> actions = new ArrayList<>();
        if (workflow.getSuccessActions() != null) {
            actions.addAll(workflow.getSuccessActions());
        }
        if (executionResultContext.getSuccessAction() != null) {
            actions.add(executionResultContext.getSuccessAction());
        }
        return actions;
    }

    private List<FailureAction> collectFailureActions(final Workflow workflow, final ExecutionResultContext executionResultContext) {
        List<FailureAction> actions = new ArrayList<>();
        if (workflow.getFailureActions() != null) {
            actions.addAll(workflow.getFailureActions());
        }
        if (executionResultContext.getFailureAction() != null) {
            actions.add(executionResultContext.getFailureAction());
        }
        return actions;
    }

    private void executeToReferencedWorkflow(final ArazzoSpecification arazzo,
                                             final Step currentStep,
                                             final Map<String, Object> inputs) {
        var sourceDescription = findRelevantSourceDescriptionByReferencedWorkflowId(arazzo, currentStep.getWorkflowId());
        var refWorkflow = findWorkflowByWorkflowId(sourceDescription.getReferencedArazzo(), currentStep.getWorkflowId());

        log.info("Step '{}' delegates by reference: workflowId='{}'", currentStep.getStepId(), refWorkflow.getWorkflowId());
        var workflowExecutor = new WorkflowExecutor(sourceDescription.getReferencedArazzo(), inputs);
        workflowExecutor.executeWorkflow(refWorkflow);
    }

    private void doWait(final Long seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            log.error("Wait-Timer was interrupted: {}", e.getMessage());
            throw new ItarazzoInterruptException(e);
        }
    }

    private void addResolvedOutputs(final String workflowId, final Map<String, Object> outputs) {
        outputs.forEach((key, value) ->
                resolver.addResolved("$workflows.%s.outputs.%s".formatted(workflowId, key), value));
    }

    @Data
    @Builder
    private static class ExecutionDecision {
        private Integer nextStepIndex;
        private boolean mustEnd;
    }
}
