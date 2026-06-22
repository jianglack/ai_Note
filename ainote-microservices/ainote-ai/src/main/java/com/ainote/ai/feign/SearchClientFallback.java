package com.ainote.ai.feign;

import com.ainote.common.model.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchClientFallback implements SearchClient {

    @Override
    public List<SearchResult> hybridSearch(Map<String, Object> request) {
        return List.of();
    }
}
