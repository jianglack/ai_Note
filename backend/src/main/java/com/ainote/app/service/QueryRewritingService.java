package com.ainote.app.service;

import com.ainote.app.config.CacheConfig;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class QueryRewritingService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewritingService.class);

    @Value("${app.deepseek.api-key:}")
    private String apiKey;

    @Value("${app.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${app.deepseek.model:deepseek-chat}")
    private String chatModel;

    @Value("${app.rag.query-rewriting.enabled:true}")
    private boolean enabled;

    @Value("${app.rag.query-rewriting.strategy:expand}")
    private String strategy;

    @Value("${app.rag.query-rewriting.max-variants:3}")
    private int maxVariants;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;

    public QueryRewritingService(PromptLoader promptLoader, ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    @Cacheable(cacheNames = CacheConfig.QUERY_REWRITE_CACHE,
            key = "#originalQuery",
            unless = "#result == null || #result.isEmpty()")
    public List<String> rewriteQuery(String originalQuery) {
        if (!enabled || originalQuery == null || originalQuery.isBlank()) {
            return normalizeQueryVariants(originalQuery, List.of());
        }

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("API Key not configured, query rewriting disabled");
            return normalizeQueryVariants(originalQuery, List.of());
        }

        try {
            log.info("Rewriting query with strategy '{}': {}", strategy, originalQuery);
            List<String> rewrittenQueries = switch (strategy) {
                case "expand" -> expandQuery(originalQuery);
                case "simplify" -> simplifyQuery(originalQuery);
                case "multi-angle" -> multiAngleQuery(originalQuery);
                default -> List.of(originalQuery);
            };

            List<String> normalizedQueries = normalizeQueryVariants(originalQuery, rewrittenQueries);
            log.info("Query rewritten into {} variants", normalizedQueries.size());
            return normalizedQueries;
        } catch (Exception e) {
            log.error("Error rewriting query: {}", e.getMessage(), e);
            return normalizeQueryVariants(originalQuery, List.of());
        }
    }

    List<String> normalizeQueryVariants(String originalQuery, List<String> rewrittenQueries) {
        int limit = Math.max(1, maxVariants);
        List<String> result = new ArrayList<>();
        addQueryVariant(result, originalQuery, limit);
        if (rewrittenQueries != null) {
            for (String query : rewrittenQueries) {
                addQueryVariant(result, query, limit);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private void addQueryVariant(List<String> result, String query, int limit) {
        if (result.size() >= limit || query == null || query.isBlank()) {
            return;
        }
        String normalized = query.trim();
        if (!result.contains(normalized)) {
            result.add(normalized);
        }
    }

    private List<String> expandQuery(String query) {
        String prompt = promptLoader.format("query-rewrite-expand.txt", query);
        String response = callAI(prompt);
        return parseQueryList(response);
    }

    private List<String> simplifyQuery(String query) {
        String prompt = promptLoader.format("query-rewrite-simplify.txt", query);
        String response = callAI(prompt);
        return parseQueryList(response);
    }

    private List<String> multiAngleQuery(String query) {
        String prompt = promptLoader.format("query-rewrite-multi-angle.txt", query);
        String response = callAI(prompt);
        return parseQueryList(response);
    }

    private String callAI(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", chatModel);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 500);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("AI API error: {} - {}", response.statusCode(), response.body());
                return "";
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            return responseJson.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("Error calling AI API: {}", e.getMessage(), e);
            return "";
        }
    }

    private List<String> parseQueryList(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        List<String> queries = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()
                    || line.contains(":")
                    || line.matches("^\\d+[.\\s].*")) {
                continue;
            }
            line = line.replaceAll("^\\d+[.\\s]*", "");
            if (!line.isEmpty()) {
                queries.add(line);
            }
        }

        return queries;
    }

    public String getBestQuery(String originalQuery) {
        List<String> queries = rewriteQuery(originalQuery);
        return queries.isEmpty() ? originalQuery : queries.get(0);
    }
}
