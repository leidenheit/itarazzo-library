package de.leidenheit.infrastructure.utils;

import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class WorkflowSorterUtils {

    public static void sortByDependencies(final ArazzoSpecification arazzo) {

        var result = new ArrayList<Workflow>();
        var workflowMap = new LinkedHashMap<String, Workflow>();
        var visitedWorkflows = new HashSet<String>();
        var recursionStack = new ArrayDeque<String>();

        for (Workflow workflow : arazzo.getWorkflows()) {
            workflowMap.put(workflow.getWorkflowId(), workflow);
        }

        for (Workflow workflow : arazzo.getWorkflows()) {
            if (visitedWorkflows.contains(workflow.getWorkflowId())) continue;
            sortTopological(workflow, visitedWorkflows, recursionStack, result, workflowMap);
        }

        log.info("Workflows have been sorted into the following execution order:\n{}",
                IntStream.range(0, result.size())
                        .mapToObj(index -> "%d: %s depends on: %s".formatted(
                                index + 1,
                                result.get(index).getWorkflowId(),
                                result.get(index).getDependsOn()))
                        .collect(Collectors.joining("\n")));
        arazzo.setWorkflows(result);
    }

    private static void sortTopological(final Workflow currentWorkflow,
                                        final Set<String> visitedWorkflows,
                                        final Deque<String> recursionStack,
                                        final List<Workflow> sortedWorkflows,
                                        final Map<String, Workflow> workflowMap) {

        var workflowId = currentWorkflow.getWorkflowId();
        visitedWorkflows.add(workflowId);
        recursionStack.push(workflowId);

        // lookup dependencies of this workflow
        if (Objects.nonNull(currentWorkflow.getDependsOn())) {
            for (String dependency : currentWorkflow.getDependsOn()) {
                if (recursionStack.contains(dependency)) {
                    log.error("Cyclic dependency detected: workflowId={}", dependency);
                    throw new ItarazzoIllegalStateException("Unexpected cyclic dependency");
                }
                if (visitedWorkflows.contains(dependency)) continue;
                sortTopological(workflowMap.get(dependency), visitedWorkflows, recursionStack, sortedWorkflows, workflowMap);
            }
        }

        recursionStack.pop();
        // add workflow after all dependencies
        sortedWorkflows.add(currentWorkflow);
    }

    private WorkflowSorterUtils() {
    }
}
