package com.ainote.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ainote.common.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteEsSearchService {

    private final ElasticsearchClient esClient;

    public List<SearchResult> search(String query, Long userId, int limit) {
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                .index("ainote-notes")
                .query(q -> q.bool(b -> b
                    .must(m -> m.multiMatch(mm -> mm
                        .query(query)
                        .fields("title^2", "content")
                    ))
                    .filter(f -> f.term(t -> t.field("userId").value(String.valueOf(userId))))
                ))
                .highlight(h -> h
                    .fields("title", hf -> hf)
                    .fields("content", hf -> hf.fragmentSize(150).numberOfFragments(3))
                )
                .size(limit),
                Map.class
            );

            List<SearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source == null) continue;

                SearchResult result = new SearchResult();
                Object noteIdObj = source.get("noteId");
                if (noteIdObj != null) {
                    result.setNoteId(Long.parseLong(noteIdObj.toString()));
                }
                result.setTitle((String) source.get("title"));
                result.setSnippet((String) source.get("content"));
                result.setScore(hit.score() != null ? hit.score() : 0.0);
                result.setSource("es");

                // Extract highlights
                List<String> highlights = new ArrayList<>();
                if (hit.highlight() != null) {
                    hit.highlight().forEach((field, frags) -> highlights.addAll(frags));
                }
                result.setHighlights(highlights);
                results.add(result);
            }
            return results;
        } catch (IOException e) {
            log.error("ES search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
