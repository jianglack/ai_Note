package com.ainote.app.service;

import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Query Rewriting 服务
 * 使用 AI 改写用户查询，提升检索准确度
 * 
 * 支持多种改写策略：
 * 1. 查询扩展：生成同义词、相关词
 * 2. 查询简化：提取核心关键词
 * 3. 多角度查询：从不同角度理解用户意图
 */
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
    private String strategy;  // expand, simplify, multi-angle

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;

    public QueryRewritingService(PromptLoader promptLoader) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.promptLoader = promptLoader;
    }

    /**
     * 改写查询
     * @param originalQuery 原始查询
     * @return 改写后的查询列表（包含原始查询）
     */
    public List<String> rewriteQuery(String originalQuery) {
        if (!enabled || originalQuery == null || originalQuery.isBlank()) {
            return List.of(originalQuery);
        }

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("API Key not configured, query rewriting disabled");
            return List.of(originalQuery);
        }

        try {
            log.info("Rewriting query with strategy '{}': {}", strategy, originalQuery);
            
            List<String> rewrittenQueries = switch (strategy) {
                case "expand" -> expandQuery(originalQuery);
                case "simplify" -> simplifyQuery(originalQuery);
                case "multi-angle" -> multiAngleQuery(originalQuery);
                default -> List.of(originalQuery);
            };

            // 始终包含原始查询
            if (!rewrittenQueries.contains(originalQuery)) {
                List<String> result = new ArrayList<>();
                result.add(originalQuery);
                result.addAll(rewrittenQueries);
                return result;
            }

            log.info("Query rewritten into {} variants", rewrittenQueries.size());
            return rewrittenQueries;

        } catch (Exception e) {
            log.error("Error rewriting query: {}", e.getMessage(), e);
            return List.of(originalQuery);
        }
    }

    /**
     * 查询扩展：生成同义词和相关词
     */
    private List<String> expandQuery(String query) {
        String prompt = promptLoader.format("query-rewrite-expand.txt", query);
        String response = callAI(prompt);
        return parseQueryList(response);
    }

    /**
     * 查询简化：提取核心关键词
     */
    private List<String> simplifyQuery(String query) {
        String prompt = promptLoader.format("query-rewrite-simplify.txt", query);
        String response = callAI(prompt);
        return parseQueryList(response);
    }

    /**
     * 多角度查询：从不同角度理解用户意图
     */
    private List<String> multiAngleQuery(String query) {
        String prompt = promptLoader.format("query-rewrite-multi-angle.txt", query);
        String response = callAI(prompt);
        return parseQueryList(response);
    }

    /**
     * 调用 DeepSeek OpenAI-compatible API
     */
    private String callAI(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", chatModel);
            requestBody.put("temperature", 0.3);  // 较低温度保证稳定性
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

    /**
     * 解析 AI 返回的查询列表
     */
    private List<String> parseQueryList(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        List<String> queries = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            line = line.trim();
            // 跳过空行、标题行、编号行
            if (line.isEmpty() || 
                line.contains("：") || 
                line.contains(":") ||
                line.matches("^\\d+[.、].*")) {
                continue;
            }
            // 移除可能的编号前缀
            line = line.replaceAll("^\\d+[.、]\\s*", "");
            if (!line.isEmpty()) {
                queries.add(line);
            }
        }

        return queries;
    }

    /**
     * 获取最佳查询（用于单查询场景）
     */
    public String getBestQuery(String originalQuery) {
        List<String> queries = rewriteQuery(originalQuery);
        return queries.isEmpty() ? originalQuery : queries.get(0);
    }
}
