package com.ainote.search.controller;

import com.ainote.common.model.SearchRequest;
import com.ainote.common.model.SearchResult;
import com.ainote.common.security.UserContext;
import com.ainote.search.service.HybridSearchService;
import com.ainote.search.service.NoteEsSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final NoteEsSearchService esSearchService;
    private final HybridSearchService hybridSearchService;

    @PostMapping("/fulltext")
    public List<SearchResult> fulltext(@RequestBody SearchRequest req) {
        Long userId = UserContext.getCurrentUserId();
        return esSearchService.search(req.getQuery(), userId, req.getLimit() > 0 ? req.getLimit() : 10);
    }

    @PostMapping("/hybrid")
    public List<SearchResult> hybrid(@RequestBody SearchRequest req) {
        Long userId = UserContext.getCurrentUserId();
        return hybridSearchService.hybridSearch(req.getQuery(), userId, req.getLimit() > 0 ? req.getLimit() : 10);
    }

    @PostMapping("/reindex")
    public ResponseEntity<String> reindex() {
        return ResponseEntity.ok("Reindex triggered");
    }
}
