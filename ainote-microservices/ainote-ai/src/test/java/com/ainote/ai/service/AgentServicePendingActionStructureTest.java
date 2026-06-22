package com.ainote.ai.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServicePendingActionStructureTest {

    @Test
    void agentServiceUsesTypedRegistryInsteadOfPendingActionRegex() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "ai", "service", "AgentService.java"));

        assertThat(source)
                .contains("PendingActionRegistry")
                .doesNotContain("PENDING_ACTION_PATTERN")
                .doesNotContain("Pattern.compile(\"PENDING_ACTION")
                .doesNotContain("Matcher matcher = PENDING_ACTION_PATTERN")
                .doesNotContain("new ObjectMapper()");
    }
}
