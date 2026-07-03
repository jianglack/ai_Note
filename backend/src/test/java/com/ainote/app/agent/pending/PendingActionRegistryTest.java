package com.ainote.app.agent.pending;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PendingActionRegistryTest {

    @Test
    void preservesNestedActionFieldsAsStructuredValues() {
        PendingActionRegistry registry = new PendingActionRegistry(new ObjectMapper());
        String response = """
                PENDING_ACTION:{"type":"DELETE_NOTE","noteId":"note-1","metadata":{"priority":"high"},"tags":["a","b"]}
                Confirm?
                """;

        List<Map<String, Object>> actions = registry.extractActions(response);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).get("metadata"))
                .isInstanceOf(Map.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("priority", "high");
        assertThat(actions.get(0).get("tags"))
                .isInstanceOf(List.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("a", "b");
    }

    @Test
    void acceptsDeleteNotesWithAllActiveNotesScope() {
        PendingActionRegistry registry = new PendingActionRegistry(new ObjectMapper());

        List<Map<String, Object>> actions = registry.extractActions(
                "PENDING_ACTION:{\"type\":\"DELETE_NOTES\",\"scope\":\"ALL_ACTIVE_NOTES\"}");

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0))
                .containsEntry("type", "DELETE_NOTES")
                .containsEntry("scope", "ALL_ACTIVE_NOTES");
    }

    @Test
    void rejectsDeleteNotesWithoutIdsOrScope() {
        PendingActionRegistry registry = new PendingActionRegistry(new ObjectMapper());

        List<Map<String, Object>> actions = registry.extractActions(
                "PENDING_ACTION:{\"type\":\"DELETE_NOTES\",\"count\":\"31\"}");

        assertThat(actions).isEmpty();
    }

    @Test
    void rejectsDeleteNotesWithUnknownScope() {
        PendingActionRegistry registry = new PendingActionRegistry(new ObjectMapper());

        List<Map<String, Object>> actions = registry.extractActions(
                "PENDING_ACTION:{\"type\":\"DELETE_NOTES\",\"scope\":\"ALL_USERS_NOTES\"}");

        assertThat(actions).isEmpty();
    }

    @Test
    void registryIsInjectedInsteadOfConstructedInsideServices() throws Exception {
        String agentService = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "app", "service", "AgentService.java"));
        String postExecutionHandler = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "app", "agent", "pipeline", "PostExecutionHandler.java"));

        assertThat(agentService).doesNotContain("new PendingActionRegistry");
        assertThat(agentService).doesNotContain("new ObjectMapper()");
        assertThat(postExecutionHandler).doesNotContain("new PendingActionRegistry");
        assertThat(postExecutionHandler).doesNotContain("new ObjectMapper()");
    }
}
