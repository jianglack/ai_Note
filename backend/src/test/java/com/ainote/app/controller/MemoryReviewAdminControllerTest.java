package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryReplayCandidateExportResponse;
import com.ainote.app.model.memory.MemoryReviewCaseListResponse;
import com.ainote.app.model.memory.MemoryReviewCaseResponse;
import com.ainote.app.model.memory.MemoryReviewDecisionRequest;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.security.SecurityUtils;
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

@WebMvcTest(MemoryReviewAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemoryReviewAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemoryReviewService memoryReviewService;

    @MockBean
    private SecurityUtils securityUtils;

    @MockBean
    private AdminAccessGuard adminAccessGuard;

    @Test
    void listReviewCasesRequiresAdminGuard() throws Exception {
        Mockito.when(memoryReviewService.listReviewCases("pending_review", 25))
                .thenReturn(new MemoryReviewCaseListResponse(List.of(reviewCaseResponse("pending_review"))));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/memory-review-cases")
                        .param("status", "pending_review")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(201))
                .andExpect(jsonPath("$.items[0].status").value("pending_review"));

        Mockito.verify(adminAccessGuard).checkAdminAccess();
        Mockito.verify(memoryReviewService).listReviewCases("pending_review", 25);
    }

    @Test
    void decideReviewCaseRequiresAdminGuardAndReviewerId() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("admin-1");
        MemoryReviewDecisionRequest request = new MemoryReviewDecisionRequest(
                "approve_replay", "confirmed", null, null, null);
        Mockito.when(memoryReviewService.decideReviewCase(
                        eq(201L), eq("admin-1"), Mockito.any(MemoryReviewDecisionRequest.class)))
                .thenReturn(reviewCaseResponse("approved_for_replay"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/memory-review-cases/201/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(201))
                .andExpect(jsonPath("$.status").value("approved_for_replay"));

        Mockito.verify(adminAccessGuard).checkAdminAccess();
        Mockito.verify(memoryReviewService)
                .decideReviewCase(eq(201L), eq("admin-1"), Mockito.any(MemoryReviewDecisionRequest.class));
    }

    @Test
    void exportReplayCandidatesRequiresAdminGuard() throws Exception {
        Mockito.when(memoryReviewService.exportApprovedReplayCandidates(100))
                .thenReturn(new MemoryReplayCandidateExportResponse(
                        List.of(reviewCaseResponse("approved_for_replay"))));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/memory-review-cases/replay-candidates")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].replayCaseId").value("review-201"))
                .andExpect(jsonPath("$.items[0].replayCaseJson").value("{\"id\":\"review-201\"}"));

        Mockito.verify(adminAccessGuard).checkAdminAccess();
        Mockito.verify(memoryReviewService).exportApprovedReplayCandidates(100);
    }

    private static MemoryReviewCaseResponse reviewCaseResponse(String status) {
        return new MemoryReviewCaseResponse(
                201L,
                11L,
                "user-1",
                "wrong_memory",
                "wrong",
                "prefers concise Chinese",
                "preference",
                true,
                status,
                "admin-1",
                "approve_replay",
                "confirmed",
                "review-201",
                "{\"id\":\"review-201\"}",
                "{\"reviewerId\":\"admin-1\"}",
                "{\"id\":11}",
                "{\"sourceTraceId\":\"trace-11\"}",
                "{\"policy_source\":\"advisor\"}",
                LocalDateTime.parse("2026-07-09T10:00:00"),
                LocalDateTime.parse("2026-07-09T10:00:00"),
                LocalDateTime.parse("2026-07-09T10:05:00"));
    }
}
