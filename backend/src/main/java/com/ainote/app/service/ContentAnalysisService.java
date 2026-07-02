package com.ainote.app.service;

import com.ainote.app.entity.NoteConcept;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ContentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ContentAnalysisService.class);

    private final ChatModel chatModel;
    private final NoteConceptRepository conceptRepository;
    private final NoteRepository noteRepository;
    private final KnowledgeGraphService knowledgeGraphService;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    public ContentAnalysisService(
            ChatModel chatModel,
            NoteConceptRepository conceptRepository,
            NoteRepository noteRepository,
            KnowledgeGraphService knowledgeGraphService,
            PromptLoader promptLoader,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.conceptRepository = conceptRepository;
        this.noteRepository = noteRepository;
        this.knowledgeGraphService = knowledgeGraphService;
        this.promptLoader = promptLoader;
        this.objectMapper = objectMapper;
    }

    @Async("taskExecutor")
    @Transactional
    public void extractConceptsAsync(String noteId, String userId, String title, String content) {
        try {
            if (content == null || content.length() < 20) {
                log.debug("Note {} content too short, skipping concept extraction", noteId);
                return;
            }
            if (!noteStillActive(noteId, userId)) {
                log.debug("Note {} is no longer active, skipping concept extraction", noteId);
                return;
            }

            String noteText = "Note title: " + (title != null ? title : "") + "\nNote content:\n"
                    + (content.length() > 2000 ? content.substring(0, 2000) : content);

            List<ChatMessage> messages = List.of(
                    SystemMessage.from(promptLoader.load("concept-extraction.txt")),
                    UserMessage.from(noteText)
            );

            ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
            String result = response.aiMessage().text();

            List<Map<String, Object>> concepts = parseConceptsJson(result);
            if (concepts.isEmpty()) {
                log.debug("No concepts extracted from note {}", noteId);
                return;
            }
            if (!noteStillActive(noteId, userId)) {
                log.debug("Note {} was removed before concept persistence, skipping", noteId);
                return;
            }

            try {
                conceptRepository.deleteByNoteId(noteId);
                conceptRepository.flush();

                for (Map<String, Object> c : concepts) {
                    NoteConcept nc = new NoteConcept();
                    nc.setNoteId(noteId);
                    nc.setUserId(userId);
                    nc.setConcept((String) c.get("concept"));
                    nc.setCategory((String) c.getOrDefault("category", "keyword"));
                    nc.setConfidence(((Number) c.getOrDefault("confidence", 0.8)).doubleValue());
                    conceptRepository.save(nc);
                }
            } catch (DataIntegrityViolationException e) {
                log.debug("Note {} was removed during concept persistence, skipping", noteId);
                return;
            }

            if (knowledgeGraphService.isNeo4jEnabled()) {
                knowledgeGraphService.syncNoteConcepts(noteId, concepts);
            }

            log.info("Extracted {} concepts from note {}", concepts.size(), noteId);
        } catch (Exception e) {
            log.error("Failed to extract concepts from note {}: {}", noteId, e.getMessage());
        }
    }

    private boolean noteStillActive(String noteId, String userId) {
        return noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId).isPresent();
    }

    private List<Map<String, Object>> parseConceptsJson(String response) {
        try {
            String json = response.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            } else if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse concepts JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
