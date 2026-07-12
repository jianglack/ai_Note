package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryListResponse;
import com.ainote.app.model.memory.MemoryDeleteProofResponse;
import com.ainote.app.model.memory.MemoryEventListResponse;
import com.ainote.app.model.memory.MemoryEventResponse;
import com.ainote.app.model.memory.MemoryExportResponse;
import com.ainote.app.model.memory.MemoryFeedbackRequest;
import com.ainote.app.model.memory.MemoryResponse;
import com.ainote.app.model.memory.MemoryReviewCaseResponse;
import com.ainote.app.model.memory.MemoryUpdateRequest;
import com.ainote.app.model.memory.MemoryForgetRequest;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.MemoryControlService;
import com.ainote.app.service.MemoryReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemoryControlService memoryControlService;

    @MockBean
    private MemoryReviewService memoryReviewService;

    @MockBean
    private SecurityUtils securityUtils;

    @Test
    void listMemoriesUsesCurrentUserAndFilters() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        Mockito.when(memoryControlService.listMemories("user-1", "preference", "active", "markdown", null))
                .thenReturn(new MemoryListResponse(List.of(memoryResponse(1L, "active")), null));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/memories")
                        .param("type", "preference")
                        .param("status", "active")
                        .param("query", "markdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].status").value("active"))
                .andExpect(jsonPath("$.items[0].metadataJson").value("{\"capture_reason\":\"explicit memory request\"}"));

        Mockito.verify(memoryControlService)
                .listMemories("user-1", "preference", "active", "markdown", null);
    }

    @Test
    void listEventsUsesCurrentUserAndOptionalMemoryFilter() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        Mockito.when(memoryControlService.listEvents("user-1", 1L, 25))
                .thenReturn(new MemoryEventListResponse(List.of(memoryEventResponse())));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/memories/events")
                        .param("memoryId", "1")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(101))
                .andExpect(jsonPath("$.items[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$.items[0].traceId").value("memory-capture-abc"));

        Mockito.verify(memoryControlService).listEvents("user-1", 1L, 25);
    }

    @Test
    void listEventsCanReturnRecentLedgerRowsWithoutMemoryFilter() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        Mockito.when(memoryControlService.listEvents("user-1", null, 50))
                .thenReturn(new MemoryEventListResponse(List.of(memoryEventResponse())));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/memories/events")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventType").value("CREATED"));

        Mockito.verify(memoryControlService).listEvents("user-1", null, 50);
    }

    @Test
    void patchMemoryUsesCurrentUser() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        MemoryUpdateRequest request = new MemoryUpdateRequest(
                "updated", "disabled", "preference", "user", 0.8, "wrong memory");
        Mockito.when(memoryControlService.updateMemory(eq("user-1"), eq(1L), Mockito.any(MemoryUpdateRequest.class)))
                .thenReturn(memoryResponse(1L, "disabled"));

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/memories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        Mockito.verify(memoryControlService)
                .updateMemory(eq("user-1"), eq(1L), Mockito.any(MemoryUpdateRequest.class));
    }

    @Test
    void deleteMemoryUsesCurrentUserAndReturnsNoContent() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/memories/1"))
                .andExpect(status().isNoContent());

        Mockito.verify(memoryControlService).deleteMemory("user-1", 1L);
    }

    @Test
    void exportUsesCurrentUser() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        Mockito.when(memoryControlService.exportMemories("user-1"))
                .thenReturn(new MemoryExportResponse(
                        List.of(memoryResponse(1L, "active")),
                        null,
                        LocalDateTime.parse("2026-07-10T10:00:00"),
                        1,
                        true,
                        java.util.Map.of(),
                        "memory-export-json-v1"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/memories/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.schemaVersion").value("memory-export-json-v1"))
                .andExpect(jsonPath("$.redacted").value(true));

        Mockito.verify(memoryControlService).exportMemories("user-1");
    }

    @Test
    void forgetUsesCurrentUser() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        MemoryForgetRequest request = new MemoryForgetRequest(List.of(1L), null, "forget");
        Mockito.when(memoryControlService.forgetMemories(eq("user-1"), Mockito.any(MemoryForgetRequest.class)))
                .thenReturn(new MemoryForgetResponse(1));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/memories/forget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(1));

        Mockito.verify(memoryControlService)
                .forgetMemories(eq("user-1"), Mockito.any(MemoryForgetRequest.class));
    }

    @Test
    void hardDeleteUsesCurrentUserAndReturnsProof() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        MemoryForgetRequest request = new MemoryForgetRequest(List.of(1L), null, "hard delete");
        Mockito.when(memoryControlService.hardDeleteMemories(eq("user-1"), Mockito.any(MemoryForgetRequest.class)))
                .thenReturn(new MemoryDeleteProofResponse(
                        "memory-delete-1",
                        "user-1",
                        List.of(1L),
                        1,
                        List.of("hash-1"),
                        LocalDateTime.parse("2026-07-10T10:00:00"),
                        LocalDateTime.parse("2026-07-10T10:00:01"),
                        "completed",
                        "{\"status\":\"completed\"}"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/memories/hard-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("memory-delete-1"))
                .andExpect(jsonPath("$.deletedCount").value(1))
                .andExpect(jsonPath("$.status").value("completed"));

        Mockito.verify(memoryControlService)
                .hardDeleteMemories(eq("user-1"), Mockito.any(MemoryForgetRequest.class));
    }

    @Test
    void reportMemoryFeedbackUsesCurrentUser() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        MemoryFeedbackRequest request = new MemoryFeedbackRequest(
                "wrong_memory", "wrong", "prefers concise Chinese", "preference", true);
        Mockito.when(memoryReviewService.createFeedback(
                        eq("user-1"), eq(11L), Mockito.any(MemoryFeedbackRequest.class)))
                .thenReturn(memoryReviewCaseResponse());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/memories/11/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(201))
                .andExpect(jsonPath("$.memoryId").value(11))
                .andExpect(jsonPath("$.status").value("pending_review"));

        Mockito.verify(memoryReviewService)
                .createFeedback(eq("user-1"), eq(11L), Mockito.any(MemoryFeedbackRequest.class));
    }

    private static MemoryResponse memoryResponse(Long id, String status) {
        return new MemoryResponse(
                id,
                "semantic",
                "preference",
                "preference",
                "prefers markdown",
                0.9,
                "ai_extracted",
                "user",
                status,
                null,
                null,
                null,
                null,
                "{\"capture_reason\":\"explicit memory request\"}",
                null,
                0,
                null,
                LocalDateTime.parse("2026-07-03T10:00:00"),
                LocalDateTime.parse("2026-07-03T10:00:00"));
    }

    private static MemoryEventResponse memoryEventResponse() {
        return new MemoryEventResponse(
                101L,
                1L,
                "CREATED",
                "assistant",
                "explicit_memory",
                null,
                "{\"content\":\"prefers markdown\"}",
                "memory-capture-abc",
                LocalDateTime.parse("2026-07-03T10:05:00"));
    }

    private static MemoryReviewCaseResponse memoryReviewCaseResponse() {
        return new MemoryReviewCaseResponse(
                201L,
                11L,
                "user-1",
                "wrong_memory",
                "wrong",
                "prefers concise Chinese",
                "preference",
                true,
                "pending_review",
                null,
                null,
                null,
                null,
                null,
                null,
                "{\"id\":11}",
                "{\"sourceTraceId\":\"trace-11\"}",
                "{\"policy_source\":\"advisor\"}",
                LocalDateTime.parse("2026-07-09T10:00:00"),
                LocalDateTime.parse("2026-07-09T10:00:00"),
                null);
    }
}
