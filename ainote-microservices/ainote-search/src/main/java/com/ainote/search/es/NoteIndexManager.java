package com.ainote.search.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class NoteIndexManager {

    private final ElasticsearchClient esClient;
    private static final String INDEX_NAME = "ainote-notes";

    @PostConstruct
    public void init() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (!exists) {
                createIndex();
                log.info("Created ES index: {}", INDEX_NAME);
            } else {
                log.info("ES index already exists: {}", INDEX_NAME);
            }
        } catch (IOException e) {
            log.warn("Failed to check/create ES index: {}", e.getMessage());
        }
    }

    private void createIndex() throws IOException {
        esClient.indices().create(c -> c
            .index(INDEX_NAME)
            .settings(s -> s
                .numberOfShards("1")
                .numberOfReplicas("0")
                .analysis(a -> a
                    .analyzer("ik_smart_analyzer", an -> an
                        .custom(cu -> cu.tokenizer("ik_smart").filter("lowercase")))
                    .analyzer("ik_max_analyzer", an -> an
                        .custom(cu -> cu.tokenizer("ik_max_word").filter("lowercase")))
                )
            )
            .mappings(m -> m
                .properties("noteId", p -> p.keyword(k -> k))
                .properties("userId", p -> p.keyword(k -> k))
                .properties("title", p -> p.text(t -> t
                    .analyzer("ik_max_analyzer")
                    .searchAnalyzer("ik_smart_analyzer")))
                .properties("content", p -> p.text(t -> t
                    .analyzer("ik_max_analyzer")
                    .searchAnalyzer("ik_smart_analyzer")))
                .properties("updatedAt", p -> p.date(d -> d))
            )
        );
    }
}
