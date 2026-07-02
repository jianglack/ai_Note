package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteConcept;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContentAnalysisServiceTest {

    private ChatModel chatModel;
    private NoteConceptRepository conceptRepository;
    private NoteRepository noteRepository;
    private KnowledgeGraphService knowledgeGraphService;
    private PromptLoader promptLoader;
    private ContentAnalysisService service;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        conceptRepository = mock(NoteConceptRepository.class);
        noteRepository = mock(NoteRepository.class);
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        promptLoader = mock(PromptLoader.class);
        service = new ContentAnalysisService(
                chatModel,
                conceptRepository,
                noteRepository,
                knowledgeGraphService,
                promptLoader,
                new ObjectMapper());
    }

    @Test
    void extractConceptsAsync_validResponse_replacesConceptsAndSyncsGraph() {
        givenActiveNote();
        when(promptLoader.load("concept-extraction.txt")).thenReturn("extract concepts");
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                ```json
                [
                  {"concept":"java","category":"topic","confidence":0.92},
                  {"concept":"spring"}
                ]
                ```
                """));
        when(knowledgeGraphService.isNeo4jEnabled()).thenReturn(true);

        service.extractConceptsAsync("note-1", "user-1", "title",
                "This content is intentionally long enough for concept extraction.");

        ArgumentCaptor<NoteConcept> captor = ArgumentCaptor.forClass(NoteConcept.class);
        verify(conceptRepository).deleteByNoteId("note-1");
        verify(conceptRepository).flush();
        verify(conceptRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(NoteConcept::getConcept)
                .containsExactly("java", "spring");
        assertThat(captor.getAllValues().get(0).getCategory()).isEqualTo("topic");
        assertThat(captor.getAllValues().get(0).getConfidence()).isEqualTo(0.92);
        assertThat(captor.getAllValues().get(1).getCategory()).isEqualTo("keyword");
        assertThat(captor.getAllValues().get(1).getConfidence()).isEqualTo(0.8);
        verify(knowledgeGraphService).syncNoteConcepts(eq("note-1"), anyList());
    }

    @Test
    void extractConceptsAsync_shortContent_skipsExternalCalls() {
        service.extractConceptsAsync("note-1", "user-1", "title", "too short");

        verifyNoInteractions(chatModel, conceptRepository, noteRepository, knowledgeGraphService, promptLoader);
    }

    @Test
    void extractConceptsAsync_invalidJson_doesNotReplaceExistingConcepts() {
        givenActiveNote();
        when(promptLoader.load("concept-extraction.txt")).thenReturn("extract concepts");
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("not json"));

        service.extractConceptsAsync("note-1", "user-1", "title",
                "This content is intentionally long enough for concept extraction.");

        verify(conceptRepository, never()).deleteByNoteId("note-1");
        verify(conceptRepository, never()).save(any(NoteConcept.class));
        verify(knowledgeGraphService, never()).syncNoteConcepts(any(), anyList());
    }

    @Test
    void extractConceptsAsync_missingNote_skipsExternalCalls() {
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.empty());

        service.extractConceptsAsync("note-1", "user-1", "title",
                "This content is intentionally long enough for concept extraction.");

        verifyNoInteractions(chatModel, conceptRepository, knowledgeGraphService, promptLoader);
    }

    @Test
    void extractConceptsAsync_deletedAfterLlm_skipsPersistence() {
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.of(new Note()), Optional.empty());
        when(promptLoader.load("concept-extraction.txt")).thenReturn("extract concepts");
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                [{"concept":"java","category":"topic","confidence":0.92}]
                """));

        service.extractConceptsAsync("note-1", "user-1", "title",
                "This content is intentionally long enough for concept extraction.");

        verify(conceptRepository, never()).deleteByNoteId("note-1");
        verify(conceptRepository, never()).save(any(NoteConcept.class));
        verify(knowledgeGraphService, never()).syncNoteConcepts(any(), anyList());
    }

    @Test
    void extractConceptsAsync_concurrentDeleteDuringPersistence_skipsGraphSync() {
        givenActiveNote();
        when(promptLoader.load("concept-extraction.txt")).thenReturn("extract concepts");
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                [{"concept":"java","category":"topic","confidence":0.92}]
                """));
        when(conceptRepository.save(any(NoteConcept.class)))
                .thenThrow(new DataIntegrityViolationException("fk"));

        service.extractConceptsAsync("note-1", "user-1", "title",
                "This content is intentionally long enough for concept extraction.");

        verify(conceptRepository).deleteByNoteId("note-1");
        verify(conceptRepository).flush();
        verify(knowledgeGraphService, never()).syncNoteConcepts(any(), anyList());
    }

    private void givenActiveNote() {
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.of(new Note()));
    }

    private static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text.trim()))
                .build();
    }
}
