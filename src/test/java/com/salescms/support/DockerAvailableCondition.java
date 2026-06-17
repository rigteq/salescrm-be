package com.salescms.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Disables an integration-test class entirely when Docker is unavailable.
 * Because this is an {@link ExecutionCondition}, it is evaluated before the
 * Spring TestContext / Testcontainers extensions run — so when Docker is
 * absent the Spring context is never created and the test DB is never touched.
 */
public class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker available");
            }
        } catch (Throwable t) {
            return ConditionEvaluationResult.disabled("Docker not available: " + t.getMessage());
        }
        return ConditionEvaluationResult.disabled("Docker not available — skipping integration tests");
    }
}
