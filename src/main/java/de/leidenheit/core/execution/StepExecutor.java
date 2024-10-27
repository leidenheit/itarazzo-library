package de.leidenheit.core.execution;

import de.leidenheit.core.execution.context.ExecutionResultContext;
import de.leidenheit.core.model.Step;
import de.leidenheit.core.model.Workflow;

public interface StepExecutor {

    ExecutionResultContext executeStep(final Workflow workflow, final Step step);
}
