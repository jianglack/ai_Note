package com.ainote.app;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (TestDatabaseProperties.adminDatabaseConfigured()) {
            return ConditionEvaluationResult.enabled("Explicit temporary database admin URL is configured");
        }
        if (TestDatabaseProperties.dockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        return ConditionEvaluationResult.disabled("Docker is not available for Testcontainers-backed tests");
    }
}
