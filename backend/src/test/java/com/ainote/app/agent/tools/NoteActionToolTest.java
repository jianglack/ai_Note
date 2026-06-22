package com.ainote.app.agent.tools;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.model.Note;
import com.ainote.app.model.NoteRequest;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AiService;
import com.ainote.app.service.NoteService;
import com.ainote.app.service.NoteVersionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("NoteActionTool")
class NoteActionToolTest {

    @Test
    @SuppressWarnings("unchecked")
    void createDedupUsesExactContentInsteadOfHashCodeCollision() {
        NoteService noteService = mock(NoteService.class);
        NoteRepository noteRepository = mock(NoteRepository.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        ToolExecutionPipeline pipeline = mock(ToolExecutionPipeline.class);
        NoteActionTool tool = new NoteActionTool(
                noteService,
                noteRepository,
                securityUtils,
                mock(AiService.class),
                mock(NoteVersionService.class),
                pipeline
        );

        com.ainote.app.entity.Note existing = new com.ainote.app.entity.Note();
        existing.setId("existing");
        existing.setTitle("Collision");
        existing.setContent("BB");

        Note created = new Note();
        created.setId("created");
        created.setTitle("Collision");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findRecentByUserIdAndTitle(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(existing));
        when(noteService.create(any(NoteRequest.class))).thenReturn(created);
        when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<String>>getArgument(4).get());

        String result = tool.noteAction("create", "{\"title\":\"Collision\",\"content\":\"Aa\"}");

        assertThat(result).contains("已创建笔记");
        verify(noteService).create(argThat(request ->
                "Collision".equals(request.getTitle()) && "Aa".equals(request.getContent())));
    }
}
