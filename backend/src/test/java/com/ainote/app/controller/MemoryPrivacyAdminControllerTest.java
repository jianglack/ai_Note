package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryCompliancePostureResponse;
import com.ainote.app.model.memory.MemoryRetentionPurgeResponse;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.service.MemoryControlService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemoryPrivacyAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemoryPrivacyAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryControlService memoryControlService;

    @MockBean
    private AdminAccessGuard adminAccessGuard;

    @Test
    void purgeExpiredDeletedMemoriesRequiresAdminGuard() throws Exception {
        Mockito.when(memoryControlService.purgeExpiredDeletedMemories(any(LocalDateTime.class)))
                .thenReturn(new MemoryRetentionPurgeResponse(
                        1,
                        List.of(10L),
                        LocalDateTime.parse("2026-06-10T10:00:00"),
                        LocalDateTime.parse("2026-07-10T10:00:00"),
                        "completed"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/memory-privacy/retention/purge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedCount").value(1))
                .andExpect(jsonPath("$.status").value("completed"));

        Mockito.verify(adminAccessGuard).checkAdminAccess("memory_privacy_retention_purge");
        Mockito.verify(memoryControlService).purgeExpiredDeletedMemories(any(LocalDateTime.class));
    }

    @Test
    void compliancePostureRequiresAdminGuard() throws Exception {
        Mockito.when(memoryControlService.compliancePosture())
                .thenReturn(new MemoryCompliancePostureResponse(
                        true,
                        true,
                        30,
                        true,
                        false,
                        "",
                        "action_required"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/memory-privacy/posture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.piiDetectionEnabled").value(true))
                .andExpect(jsonPath("$.status").value("action_required"));

        Mockito.verify(adminAccessGuard).checkAdminAccess("memory_privacy_posture_read");
        Mockito.verify(memoryControlService).compliancePosture();
    }
}
