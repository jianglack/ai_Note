package com.ainote.app.controller;

import com.ainote.app.model.Note;
import com.ainote.app.model.NoteRequest;
import com.ainote.app.service.NoteService;
import com.ainote.app.service.NoteVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@WebMvcTest(NoteController.class)
@AutoConfigureMockMvc(addFilters = false)
public class NoteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NoteService noteService;

    @MockBean
    private NoteVersionService noteVersionService;

    @Autowired
    private ObjectMapper objectMapper;

    private Note note;
    private NoteRequest noteRequest;
    private List<Note> noteList;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        note = new Note();
        note.setId("note-123");
        note.setTitle("Test Note");
        note.setContent("This is a test note");
        note.setCreatedAt("2026-04-08");

        noteRequest = new NoteRequest();
        noteRequest.setTitle("Test Note");
        noteRequest.setContent("This is a test note");
        noteRequest.setFolderId("folder-123");

        noteList = new ArrayList<>();
        noteList.add(note);
    }

    @Test
    @DisplayName("GET /api/notes 应返回所有笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldListAllNotes() throws Exception {
        PageRequest pageable = PageRequest.of(0, 100);
        Mockito.when(noteService.listAllPaged(pageable))
                .thenReturn(new PageImpl<>(noteList, pageable, 1));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notes"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value("note-123"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].title").value("Test Note"));

        Mockito.verify(noteService).listAllPaged(pageable);
        Mockito.verify(noteService, Mockito.never()).listAll();
    }

    @Test
    @DisplayName("GET /api/notes with page and size should return a Page")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldListNotesWithPagination() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        Mockito.when(noteService.listAllPaged(pageable))
                .thenReturn(new PageImpl<>(noteList, pageable, 21));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notes")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value("note-123"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(21))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(2));

        Mockito.verify(noteService).listAllPaged(pageable);
        Mockito.verify(noteService, Mockito.never()).listAll();
    }

    @Test
    @DisplayName("POST /api/notes 应创建新笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldCreateNote() throws Exception {
        Mockito.when(noteService.create(Mockito.any(NoteRequest.class))).thenReturn(note);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noteRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value("note-123"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Test Note"));
    }

    @Test
    @DisplayName("GET /api/notes/{id} 应返回存在的笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldGetExistingNote() throws Exception {
        Mockito.when(noteService.getById("note-123")).thenReturn(Optional.of(note));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notes/note-123"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value("note-123"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Test Note"));
    }

    @Test
    @DisplayName("GET /api/notes/{id} 应在笔记不存在时返回404")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldReturnNotFoundWhenNoteDoesNotExist() throws Exception {
        Mockito.when(noteService.getById("note-123")).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notes/note-123"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/notes/{id} 应更新存在的笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldUpdateExistingNote() throws Exception {
        Mockito.when(noteService.update(Mockito.eq("note-123"), Mockito.any(NoteRequest.class))).thenReturn(Optional.of(note));

        mockMvc.perform(MockMvcRequestBuilders.put("/api/notes/note-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noteRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value("note-123"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Test Note"));
    }

    @Test
    @DisplayName("PUT /api/notes/{id} 应在笔记不存在时返回404")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldReturnNotFoundWhenUpdatingNonExistingNote() throws Exception {
        Mockito.when(noteService.update(Mockito.eq("note-123"), Mockito.any(NoteRequest.class))).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/notes/note-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noteRequest)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/notes/{id} 应删除笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldDeleteNote() throws Exception {
        Mockito.doNothing().when(noteService).delete("note-123");

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/notes/note-123"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/notes/search 应搜索笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldSearchNotes() throws Exception {
        Mockito.when(noteService.hybridSearch("test")).thenReturn(noteList);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notes/search")
                .param("q", "test"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value("note-123"));
    }

    @Test
    @DisplayName("GET /api/notes/search 应在查询为空时返回所有笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldReturnAllNotesWhenSearchQueryIsEmpty() throws Exception {
        PageRequest pageable = PageRequest.of(0, 100);
        Mockito.when(noteService.listAllPaged(pageable))
                .thenReturn(new PageImpl<>(noteList, pageable, 1));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notes/search"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value("note-123"));

        Mockito.verify(noteService).listAllPaged(pageable);
        Mockito.verify(noteService, Mockito.never()).listAll();
    }

    @Test
    @DisplayName("GET /api/notes/trash 应返回回收站笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldReturnTrashNotes() throws Exception {
        Mockito.when(noteService.listDeleted()).thenReturn(noteList);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notes/trash"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value("note-123"));
    }

    @Test
    @DisplayName("POST /api/notes/{id}/restore 应恢复笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldRestoreNote() throws Exception {
        Mockito.doNothing().when(noteService).restore("note-123");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/notes/note-123/restore"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/notes/{id}/permanent 应永久删除笔记")
    @WithMockUser(username = "test", roles = {"USER"})
    void shouldPermanentDeleteNote() throws Exception {
        Mockito.doNothing().when(noteService).permanentDelete("note-123");

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/notes/note-123/permanent"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }
}
