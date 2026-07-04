package com.ainote.app.controller;

import com.ainote.app.model.context.ContextPreviewRequest;
import com.ainote.app.model.context.ContextPreviewResponse;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.ContextPreviewService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContextPreviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContextPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContextPreviewService contextPreviewService;

    @MockBean
    private SecurityUtils securityUtils;

    @Test
    void previewUsesCurrentUserAndReturnsContextBreakdown() throws Exception {
        Mockito.when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        Mockito.when(contextPreviewService.preview(eq("user-1"), any(ContextPreviewRequest.class)))
                .thenReturn(new ContextPreviewResponse(
                        "总结这篇笔记",
                        List.of("note-1"),
                        1,
                        "STANDARD",
                        42,
                        168,
                        "<selected_notes>...</selected_notes>",
                        List.of(new ContextPreviewResponse.Section(
                                "selected_notes",
                                "选中笔记",
                                true,
                                18,
                                "<selected_notes>...</selected_notes>")),
                        List.of(new ContextPreviewResponse.FlowStep(
                                1,
                                "判断问题类型",
                                "STANDARD",
                                "active"))));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/ai/context-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ContextPreviewRequest("总结这篇笔记", List.of("note-1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("STANDARD"))
                .andExpect(jsonPath("$.selectedNoteCount").value(1))
                .andExpect(jsonPath("$.sections[0].type").value("selected_notes"))
                .andExpect(jsonPath("$.flow[0].title").value("判断问题类型"));

        Mockito.verify(contextPreviewService).preview(eq("user-1"), any(ContextPreviewRequest.class));
    }
}
