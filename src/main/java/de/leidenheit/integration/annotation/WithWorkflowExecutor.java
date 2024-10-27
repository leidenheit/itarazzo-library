package de.leidenheit.integration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Deprecated(since = "Unsure if meaningful")
public @interface WithWorkflowExecutor {
    String workflowId();
}
