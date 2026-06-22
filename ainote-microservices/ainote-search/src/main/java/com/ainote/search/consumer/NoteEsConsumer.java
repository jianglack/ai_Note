package com.ainote.search.consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ainote.search.util.HtmlStripper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NoteEsConsumer {

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "pg-notes.public.notes", groupId = "ainote-es-consumer")
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            boolean deleted = node.has("__deleted") && "true".equals(node.get("__deleted").asText());
            String noteId = node.get("id").asText();

            if (deleted) {
                esClient.delete(d -> d.index("ainote-notes").id(noteId));
                log.info("Deleted note from ES: {}", noteId);
            } else {
                String title = node.has("title") ? node.get("title").asText() : "";
                String content = HtmlStripper.strip(node.has("content") ? node.get("content").asText() : "");
                String userId = node.get("user_id").asText();
                String updatedAt = node.has("updated_at") ? node.get("updated_at").asText() : null;

                Map<String, Object> doc = new HashMap<>();
                doc.put("noteId", noteId);
                doc.put("userId", userId);
                doc.put("title", title);
                doc.put("content", content);
                if (updatedAt != null) doc.put("updatedAt", updatedAt);

                esClient.index(i -> i.index("ainote-notes").id(noteId).document(doc));
                log.info("Upserted note to ES: {}", noteId);
            }
        } catch (Exception e) {
            log.error("Failed to process CDC event for ES: {}", e.getMessage(), e);
        }
    }
}
