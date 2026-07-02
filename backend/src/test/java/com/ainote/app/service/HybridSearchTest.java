package com.ainote.app.service;

import com.ainote.app.entity.User;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.NoteVersionRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridSearchTest {

    private NoteRepository noteRepository;
    private SecurityUtils securityUtils;
    private LangChain4jRagService ragService;
    private KnowledgeGraphService knowledgeGraphService;
    private NoteService service;

    @BeforeEach
    void setUp() {
        noteRepository = mock(NoteRepository.class);
        securityUtils = mock(SecurityUtils.class);
        ragService = mock(LangChain4jRagService.class);
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        service = new NoteService(
                noteRepository,
                mock(NoteVersionRepository.class),
                mock(TagRepository.class),
                mock(FolderRepository.class),
                securityUtils,
                mock(CacheService.class),
                ragService,
                knowledgeGraphService,
                mock(ContentAnalysisService.class));
    }

    @Test
    void blankQueryUsesBoundedPagedList() {
        PageRequest page = PageRequest.of(0, 100);
        com.ainote.app.entity.Note entity = entity("note-1", "Alpha");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findNoteIdsByUserId("user-1", page)).thenReturn(new PageImpl<>(List.of("note-1"), page, 1));
        when(noteRepository.findByIdsWithTagsAndFolder(List.of("note-1"), "user-1")).thenReturn(List.of(entity));

        List<com.ainote.app.model.Note> result = service.hybridSearch(" ");

        assertThat(result).extracting(com.ainote.app.model.Note::getId).containsExactly("note-1");
        verify(noteRepository).findNoteIdsByUserId("user-1", page);
    }

    @Test
    void nonBlankQueryMergesVectorAndGraphResultsDeDuplicatesAndLimits() {
        List<com.ainote.app.model.Note> vector = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            vector.add(model("v-" + i, "Vector " + i));
        }
        List<com.ainote.app.entity.Note> graph = List.of(
                entity("v-1", "Duplicate"),
                entity("g-1", "Graph 1"),
                entity("g-2", "Graph 2"));

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(ragService.searchSimilar("project", 10)).thenReturn(vector);
        when(knowledgeGraphService.searchRelatedNotes(
                "user-1", "project", vector.stream().map(com.ainote.app.model.Note::getId).toList(), 10))
                .thenReturn(graph);

        List<com.ainote.app.model.Note> result = service.hybridSearch("project");

        assertThat(result).hasSize(10);
        assertThat(result).extracting(com.ainote.app.model.Note::getId)
                .containsExactly("v-1", "v-2", "v-3", "v-4", "v-5", "v-6", "v-7", "v-8", "v-9", "g-1");
        verify(ragService).searchSimilar("project", 10);
    }

    private static com.ainote.app.model.Note model(String id, String title) {
        com.ainote.app.model.Note note = new com.ainote.app.model.Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent("content");
        return note;
    }

    private static com.ainote.app.entity.Note entity(String id, String title) {
        User user = new User();
        user.setId("user-1");
        user.setUsername("alice");

        com.ainote.app.entity.Note note = new com.ainote.app.entity.Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent("content");
        note.setUser(user);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return note;
    }
}
