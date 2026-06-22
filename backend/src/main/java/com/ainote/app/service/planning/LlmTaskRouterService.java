package com.ainote.app.service.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class LlmTaskRouterService {

    private static final Logger log = LoggerFactory.getLogger(LlmTaskRouterService.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String systemPrompt;

    public LlmTaskRouterService(@Qualifier("agentChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
        this.systemPrompt = loadPrompt();
    }

    public TaskRouteDecision route(String query, List<String> noteIds) {
        if (query == null || query.isBlank()) {
            return TaskRouteDecision.directFallback("空请求默认进入 DIRECT_AGENT");
        }

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(buildUserPrompt(query, noteIds))
                    )
                    .build();
            String raw = chatModel.chat(request).aiMessage().text();
            return parseDecision(raw);
        } catch (Exception e) {
            log.warn("Task route model failed, falling back to DIRECT_AGENT: {}", e.getMessage());
            return TaskRouteDecision.directFallback("路由模型调用失败，默认进入 DIRECT_AGENT");
        }
    }

    private String buildUserPrompt(String query, List<String> noteIds) {
        int selectedCount = noteIds == null ? 0 : noteIds.size();
        return "用户请求：\n" + query + "\n\n当前选中笔记数量：" + selectedCount;
    }

    private TaskRouteDecision parseDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return TaskRouteDecision.directFallback("路由模型输出为空，默认进入 DIRECT_AGENT");
        }

        String clean = stripJsonEnvelope(raw);
        try {
            JsonNode root = objectMapper.readTree(clean);
            TaskRoute route = TaskRoute.valueOf(root.path("route").asText("DIRECT_AGENT"));
            double confidence = clamp(root.path("confidence").asDouble(0.0));
            String reason = root.path("reason").asText("模型未给出理由");
            boolean requiresApproval = root.path("requiresUserPlanApproval").asBoolean(route == TaskRoute.PLANNED_TASK);
            int steps = Math.max(0, root.path("estimatedToolSteps").asInt(0));
            String riskLevel = normalizeRisk(root.path("riskLevel").asText("LOW"));
            return new TaskRouteDecision(route, confidence, reason, requiresApproval, steps, riskLevel);
        } catch (Exception e) {
            log.warn("Failed to parse task route JSON: {}", clean);
            return TaskRouteDecision.directFallback("路由模型输出无法解析，默认进入 DIRECT_AGENT");
        }
    }

    private String stripJsonEnvelope(String raw) {
        String clean = raw.trim();
        if (clean.startsWith("```")) {
            clean = clean.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return clean.substring(start, end + 1);
        }
        return clean;
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private String normalizeRisk(String value) {
        String risk = value == null ? "LOW" : value.trim().toUpperCase();
        return switch (risk) {
            case "LOW", "MEDIUM", "HIGH" -> risk;
            default -> "LOW";
        };
    }

    private String loadPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/task-router-system.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load task router prompt", e);
            return "你是任务路由器。只输出 JSON，route 必须是 DIRECT_AGENT 或 PLANNED_TASK。";
        }
    }
}
