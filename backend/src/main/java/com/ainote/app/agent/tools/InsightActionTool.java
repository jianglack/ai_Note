package com.ainote.app.agent.tools;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.NoteInsightService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InsightActionTool {

    private static final Logger log = LoggerFactory.getLogger(InsightActionTool.class);
    private final NoteInsightService insightService;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolExecutionPipeline pipeline;

    public InsightActionTool(NoteInsightService insightService, SecurityUtils securityUtils,
                             ToolExecutionPipeline pipeline) {
        this.insightService = insightService;
        this.securityUtils = securityUtils;
        this.pipeline = pipeline;
    }

    @Tool(name = "insightAction", value = """
        笔记洞察分析工具。支持的 action:
        - statistics: 获取笔记统计概览（总数、分类、活跃度、热门概念）。params: {}
        - analyze: AI 深度分析（主题聚类、模式发现、智能建议）。params: {}
        - duplicates: 查找可能重复或高度相似的笔记对。params: {}
        - timeline: 查看最近活动时间线。params: {"days":7}
        """)
    public String insightAction(String action, String paramsJson) {
        String userId = securityUtils.getCurrentUserId();
        log.info("InsightAction: action={}, params={}", action, paramsJson);

        try {
            JsonNode params = (paramsJson != null && !paramsJson.isBlank())
                    ? objectMapper.readTree(paramsJson) : objectMapper.createObjectNode();

            return pipeline.execute(userId, "insightAction", action, params,
                    () -> doExecute(action, userId, params));
        } catch (Exception e) {
            log.error("InsightAction failed: {}", e.getMessage(), e);
            return "{\"error\":\"洞察分析失败: " + e.getMessage() + "\"}";
        }
    }

    private String doExecute(String action, String userId, JsonNode params) {
        try {
            return switch (action) {
                case "statistics" -> objectMapper.writeValueAsString(insightService.getStatistics(userId));
                case "analyze" -> insightService.analyzeInsights(userId);
                case "duplicates" -> {
                    List<Map<String, Object>> dups = insightService.findDuplicateCandidates(userId);
                    yield dups.isEmpty()
                            ? "{\"message\":\"没有发现高度相似的笔记\"}"
                            : objectMapper.writeValueAsString(dups);
                }
                case "timeline" -> {
                    int days = params.path("days").asInt(7);
                    List<Map<String, Object>> events = insightService.getActivityTimeline(userId, days);
                    yield events.isEmpty()
                            ? "{\"message\":\"最近 " + days + " 天没有活动记录\"}"
                            : objectMapper.writeValueAsString(events);
                }
                default -> "{\"error\":\"未知的洞察操作: " + action + "\"}";
            };
        } catch (Exception e) {
            log.error("InsightAction doExecute failed: {}", e.getMessage(), e);
            return "{\"error\":\"洞察分析失败: " + e.getMessage() + "\"}";
        }
    }
}
