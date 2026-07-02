package com.ainote.app.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainote.app.config.GlobalExceptionHandler;
import com.ainote.app.service.LinkPreviewService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LinkPreviewControllerTest {

    private LinkPreviewService linkPreviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        linkPreviewService = mock(LinkPreviewService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LinkPreviewController(linkPreviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPreview_returnsPreviewForPublicUrl() throws Exception {
        when(linkPreviewService.fetchPreview(eq("https://example.com/article")))
                .thenReturn(Map.of(
                        "url", "https://example.com/article",
                        "title", "Example Article",
                        "description", "Preview text",
                        "imageUrl", "",
                        "siteName", "example.com",
                        "faviconUrl", "https://example.com/favicon.ico"));

        mockMvc.perform(get("/api/link-preview")
                        .param("url", "https://example.com/article"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/article"))
                .andExpect(jsonPath("$.title").value("Example Article"))
                .andExpect(jsonPath("$.siteName").value("example.com"));

        verify(linkPreviewService).fetchPreview("https://example.com/article");
    }

    @Test
    void getPreview_requiresUrlParameter() throws Exception {
        mockMvc.perform(get("/api/link-preview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getPreview_returnsSafeFallbackWhenServiceRejectsPrivateUrl() throws Exception {
        when(linkPreviewService.fetchPreview(eq("http://127.0.0.1/admin")))
                .thenReturn(Map.of(
                        "url", "http://127.0.0.1/admin",
                        "title", "http://127.0.0.1/admin",
                        "description", "",
                        "imageUrl", "",
                        "siteName", "",
                        "faviconUrl", ""));

        mockMvc.perform(get("/api/link-preview")
                        .param("url", "http://127.0.0.1/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("http://127.0.0.1/admin"))
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.siteName").value(""));

        verify(linkPreviewService).fetchPreview("http://127.0.0.1/admin");
    }
}
