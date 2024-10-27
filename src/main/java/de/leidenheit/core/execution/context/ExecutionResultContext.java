package de.leidenheit.core.execution.context;

import de.leidenheit.core.model.FailureAction;
import de.leidenheit.core.model.SuccessAction;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionResultContext {
    final boolean successful;
    final SuccessAction successAction;
    final FailureAction failureAction;
}
