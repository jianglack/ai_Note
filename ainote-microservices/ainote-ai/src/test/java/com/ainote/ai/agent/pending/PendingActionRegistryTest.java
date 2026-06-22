package com.ainote.ai.agent.pending;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PendingActionRegistryTest {

    private final PendingActionRegistry registry = new PendingActionRegistry(new ObjectMapper());

    @Test
    void extractActions_handlesNestedJsonAndEscapedBraces() {
        String response = """
                Before
                PENDING_ACTION:{"type":"DELETE_NOTE","noteId":"note-1","metadata":{"reason":"has {braces} and \\"quotes\\""},"tags":["a","b"]}
                Confirm?
                """;

        List<Map<String, Object>> actions = registry.extractActions(response);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0))
                .containsEntry("type", "DELETE_NOTE")
                .containsEntry("noteId", "note-1");
        assertThat(actions.get(0).get("metadata")).isInstanceOf(Map.class);
        assertThat(actions.get(0).get("tags")).isInstanceOf(List.class);
    }

    @Test
    void extractActions_rejectsUnknownTypeAndMissingRequiredField() {
        assertThat(registry.extractActions("""
                PENDING_ACTION:{"type":"FORMAT_DISK","noteId":"note-1"}
                """)).isEmpty();

        assertThat(registry.extractActions("""
                PENDING_ACTION:{"type":"DELETE_NOTE","title":"missing noteId"}
                """)).isEmpty();
    }

    @Test
    void removeMarkers_removesValidMarkerPayloadAndKeepsText() {
        String cleaned = registry.removeMarkers("""
                before
                PENDING_ACTION:{"type":"DELETE_NOTE","noteId":"note-1"}
                Confirm? after
                """);

        assertThat(cleaned).doesNotContain("PENDING_ACTION:");
        assertThat(cleaned).contains("before");
        assertThat(cleaned).contains("Confirm? after");
    }
}
