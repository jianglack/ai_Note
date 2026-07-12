package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryMetricsSnapshotResponse;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.service.MemoryMetricsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemoryMetricsAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemoryMetricsAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryMetricsService memoryMetricsService;

    @MockBean
    private AdminAccessGuard adminAccessGuard;

    @Test
    void snapshotRequiresAdminGuardAndReturnsMetrics() throws Exception {
        Mockito.when(memoryMetricsService.snapshot())
                .thenReturn(new MemoryMetricsSnapshotResponse(
                        "normal",
                        LocalDateTime.parse("2026-07-10T10:00:00"),
                        10,
                        6,
                        2,
                        1,
                        1,
                        40.0,
                        8,
                        5,
                        5,
                        3,
                        1,
                        1,
                        1,
                        50.0,
                        20,
                        1,
                        1,
                        2,
                        5.0,
                        2,
                        3,
                        20.0,
                        12,
                        9,
                        3,
                        75.0,
                        4,
                        6,
                        2,
                        8,
                        2.0,
                        5,
                        1,
                        20.0,
                        1,
                        120.0,
                        50.0));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/memory-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("normal"))
                .andExpect(jsonPath("$.captureDecisionCount").value(10))
                .andExpect(jsonPath("$.advisorFailureRate").value(5.0))
                .andExpect(jsonPath("$.retrievalHitRate").value(75.0))
                .andExpect(jsonPath("$.wrongWriteFeedbackCount").value(1))
                .andExpect(jsonPath("$.captureP95LatencyMs").value(120.0));

        Mockito.verify(adminAccessGuard).checkAdminAccess("memory_metrics_read");
        Mockito.verify(memoryMetricsService).snapshot();
    }
}
