package com.ainote.search.service;

import com.ainote.common.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

    private final NoteEsSearchService esSearchService;

    @Value("${app.search.rrf-k:60}")
    private int rrfK;

    public List<SearchResult> hybridSearch(String query, Long userId, int limit) {
        List<SearchResult> esResults = esSearchService.search(query, userId, limit);
        return esResults;
    }

    /**
     * Reciprocal Rank Fusion: merges multiple ranked lists.
     * score(d) = sum(1 / (k + rank_i(d))) for each list i
     */
    public List<SearchResult> rrfMerge(List<List<SearchResult>> rankedLists, int limit) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, SearchResult> resultMap = new HashMap<>();

        for (List<SearchResult> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                SearchResult r = list.get(rank);
                Long key = r.getNoteId();
                scores.merge(key, 1.0 / (rrfK + rank + 1), Double::sum);
                resultMap.putIfAbsent(key, r);
            }
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(limit)
            .map(e -> {
                SearchResult r = resultMap.get(e.getKey());
                r.setScore(e.getValue());
                return r;
            })
            .collect(Collectors.toList());
    }
}
