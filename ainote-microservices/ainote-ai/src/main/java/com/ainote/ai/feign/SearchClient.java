package com.ainote.ai.feign;

import com.ainote.common.model.SearchResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "search-service", fallback = SearchClientFallback.class)
public interface SearchClient {

    @PostMapping("/api/search/hybrid")
    List<SearchResult> hybridSearch(@RequestBody Map<String, Object> request);
}
