package com.ainote.app.controller;

import com.ainote.app.model.Tag;
import com.ainote.app.service.TagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TagController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TagController 集成测试")
class TagControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @Test
    @DisplayName("GET /api/tags 应返回标签列表")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldReturnTagList() throws Exception {
        Tag tag1 = new Tag();
        tag1.setId("tag-1");
        tag1.setName("Work");

        Tag tag2 = new Tag();
        tag2.setId("tag-2");
        tag2.setName("Personal");

        when(tagService.listAll()).thenReturn(List.of(tag1, tag2));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("tag-1"))
                .andExpect(jsonPath("$[0].name").value("Work"))
                .andExpect(jsonPath("$[1].id").value("tag-2"))
                .andExpect(jsonPath("$[1].name").value("Personal"));
    }

    @Test
    @DisplayName("POST /api/tags 应创建新标签")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldCreateTag() throws Exception {
        Tag createdTag = new Tag();
        createdTag.setId("tag-123");
        createdTag.setName("New Tag");

        when(tagService.create("New Tag")).thenReturn(createdTag);

        mockMvc.perform(post("/api/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Tag\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("tag-123"))
                .andExpect(jsonPath("$.name").value("New Tag"));
    }

    @Test
    @DisplayName("DELETE /api/tags/{id} 应删除标签")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldDeleteTag() throws Exception {
        mockMvc.perform(delete("/api/tags/tag-123"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/tags/assign 应为笔记分配标签")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldAssignTags() throws Exception {
        mockMvc.perform(post("/api/tags/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noteId\": \"note-123\", \"tagIds\": [\"tag-1\", \"tag-2\"]}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/tags/assign 缺少 noteId 应返回 400")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldRejectAssignWithoutNoteId() throws Exception {
        mockMvc.perform(post("/api/tags/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagIds\": [\"tag-1\"]}"))
                .andExpect(status().isBadRequest());
    }
}
