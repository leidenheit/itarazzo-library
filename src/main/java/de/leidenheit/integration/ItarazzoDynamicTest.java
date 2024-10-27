package de.leidenheit.integration;

import com.fasterxml.jackson.databind.JsonNode;
import de.leidenheit.core.execution.WorkflowExecutor;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Workflow;
import de.leidenheit.infrastructure.io.InputsReader;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.opentest4j.TestAbortedException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
public class ItarazzoDynamicTest {

    private final AtomicBoolean shouldInterrupt = new AtomicBoolean(false);
    private Map<String, Map<String, Object>> outputsOfWorkflows = new LinkedHashMap<>();

    public Stream<DynamicTest> generateWorkflowTests(final ArazzoSpecification arazzo, final JsonNode arazzoInputs) {
        return arazzo.getWorkflows().stream()
                .map(workflow -> createDynamicTestForWorkflow(arazzo, workflow, arazzoInputs));
    }

    private DynamicTest createDynamicTestForWorkflow(final ArazzoSpecification arazzo,
                                                     final Workflow workflow,
                                                     final JsonNode arazzoInputs) {
        var workflowInputs = InputsReader.parseAndValidateInputs(arazzo, arazzoInputs, workflow.getInputs());
        return DynamicTest.dynamicTest("%s".formatted(workflow.getWorkflowId()), () -> {
            if (shouldInterrupt.get()) throw new TestAbortedException("Aborted due to previous failure");
            try {
                executeWorkflow(arazzo, workflow, workflowInputs);
            } catch (Exception e) {
                shouldInterrupt.set(true);
                log.error("Interrupting execution due to exception: {}", e.getMessage());
                throw e;
            }
        });
    }

    private void executeWorkflow(final ArazzoSpecification arazzo,
                                 final Workflow workflow,
                                 final Map<String, Object> inputs) {
        var executor = new WorkflowExecutor(arazzo, inputs, outputsOfWorkflows);
        outputsOfWorkflows = executor.executeWorkflow(workflow);
    }
}
