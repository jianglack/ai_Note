package com.ainote.ai.agent.pipeline;

import com.ainote.ai.agent.pending.PendingActionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostExecutionHandlerPendingActionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PostExecutionHandler handler = new PostExecutionHandler(
            new ToolAuditLogger(),
            new PendingActionRegistry(objectMapper));

    @AfterEach
    void tearDown() {
        ToolAuditLogger.resetTranscript();
    }

    @Test
    void handle_acceptsOnlyRegisteredPendingActionTypes() {
        ToolExecutionContext valid = context(
                "PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"noteId\":\"note-1\"}\nConfirm?");
        handler.handle(valid);

        assertThat(valid.getOutcome().status()).isEqualTo(ToolOutcome.Status.PENDING_CONFIRM);

        ToolExecutionContext invalid = context(
                "PENDING_ACTION:{\"type\":\"FORMAT_DISK\",\"noteId\":\"note-1\"}\nConfirm?");
        handler.handle(invalid);

        assertThat(invalid.getOutcome().status()).isEqualTo(ToolOutcome.Status.FAILED);
        assertThat(invalid.getOutcome().message()).contains("Invalid PENDING_ACTION");
    }

    private ToolExecutionContext context(String rawResult) {
        ObjectNode params = objectMapper.createObjectNode();
        ToolExecutionContext ctx = new ToolExecutionContext("req-1", "user-1", "noteAction", "delete", params);
        ctx.setGuardResult(GuardResult.passed());
        ctx.setRawResult(rawResult);
        return ctx;
    }
}
