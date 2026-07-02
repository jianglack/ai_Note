package com.ainote.app.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteConcept;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.KnowledgeGraphService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeActionToolTest {

    private KnowledgeGraphService graphService;
    private NoteConceptRepository conceptRepository;
    private NoteRepository noteRepository;
    private SecurityUtils securityUtils;
    private ToolExecutionPipeline pipeline;
    private KnowledgeActionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        graphService = mock(KnowledgeGraphService.class);
        conceptRepository = mock(NoteConceptRepository.class);
        noteRepository = mock(NoteRepository.class);
        securityUtils = mock(SecurityUtils.class);
        pipeline = mock(ToolExecutionPipeline.class);
        tool = new KnowledgeActionTool(graphService, conceptRepository, noteRepository,
                securityUtils, pipeline, new ObjectMapper());

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
                .thenAnswer(inv -> inv.<Supplier<String>>getArgument(4).get());
    }

    @Test
    void findRelatedReturnsMatchesAndExcludesSelf() {
        Note current = note("n1", "当前");
        Note related = note("n2", "相关");
        when(graphService.searchRelatedNotes(eq("user-1"), eq(""), eq(List.of("n1")), eq(6)))
                .thenReturn(List.of(current, related));

        String result = tool.knowledgeAction("findRelated", "{\"noteId\":\"n1\"}");

        assertThat(result).contains("\"id\":\"n2\"").contains("\"title\":\"相关\"");
        assertThat(result).doesNotContain("\"id\":\"n1\"");
    }

    @Test
    void findRelatedRejectsMissingNoteId() {
        assertThat(tool.knowledgeAction("findRelated", "{}")).contains("请提供 noteId");
    }

    @Test
    void findRelatedReturnsEmptyMessage() {
        Note current = note("n1", "当前");
        when(graphService.searchRelatedNotes(eq("user-1"), eq(""), eq(List.of("n1")), eq(6)))
                .thenReturn(List.of(current));

        String result = tool.knowledgeAction("findRelated", "{\"noteId\":\"n1\"}");

        assertThat(result).contains("没有找到相关笔记");
    }

    @Test
    void conceptCloudReturnsServiceData() {
        when(graphService.getUserConceptCloud("user-1", 20))
                .thenReturn(List.of(Map.of("concept", "Spring", "noteCount", 2)));

        String result = tool.knowledgeAction("conceptCloud", "{}");

        assertThat(result).contains("\"concept\":\"Spring\"").contains("\"noteCount\":2");
    }

    @Test
    void conceptCloudFallsBackToEmptyMessage() {
        when(graphService.getUserConceptCloud("user-1", 20)).thenReturn(List.of());
        when(conceptRepository.findByUserId("user-1")).thenReturn(List.of());

        String result = tool.knowledgeAction("conceptCloud", "{}");

        assertThat(result).contains("暂无概念数据");
    }

    @Test
    void noteConceptsRejectsMissingNoteId() {
        assertThat(tool.knowledgeAction("noteConcepts", "{}")).contains("请提供 noteId");
    }

    @Test
    void unknownActionReturnsUnknownMessage() {
        assertThat(tool.knowledgeAction("missing", "{}")).contains("未知的知识操作");
    }

    private static Note note(String id, String title) {
        Note note = mock(Note.class);
        when(note.getId()).thenReturn(id);
        when(note.getTitle()).thenReturn(title);
        return note;
    }
}
