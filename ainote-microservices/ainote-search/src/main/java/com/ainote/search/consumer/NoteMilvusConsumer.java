package com.ainote.search.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.milvus.enabled", havingValue = "true")
@Slf4j
public class NoteMilvusConsumer {

    @KafkaListener(topics = "pg-notes.public.notes", groupId = "ainote-milvus-consumer")
    public void consume(String message) {
        log.debug("Milvus consumer received CDC event (not yet implemented)");
    }
}
