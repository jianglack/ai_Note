package com.ainote.app.service;

import com.ainote.app.model.AiChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 评估服务
 * 评估两个维度：
 * 1. 工具调用准确率 — Agent 是否选择了正确的工具和操作
 * 2. E2E 任务完成率 — 最终响应是否包含预期关键信息
 *
 * 评估数据集：List<AgentEvalCase>，每个 case 包含用户输入、期望工具、期望响应关键词
 */
@Service
public class AgentEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluationService.class);

    private final AgentService agentService;

    public AgentEvaluationService(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 评估用例
     */
    public static class AgentEvalCase {
        public String userQuery;
        public String expectedToolName;     // e.g., "noteAction", "scheduleAction", "folderAction"
        public String expectedAction;       // e.g., "create", "search", "delete"
        public boolean expectSuccess;       // 期望操作成功
        public List<String> expectedResponseKeywords;  // 响应中应包含的关键词
    }

    /**
     * 单条评估结果
     */
    public static class AgentEvalResult {
        public String userQuery;
        public String expectedTool;
        public String expectedAction;
        public String actualResponse;
        public boolean toolCorrect;         // 工具是否调用正确
        public boolean taskComplete;        // E2E 任务是否完成
        public List<String> matchedKeywords;
        public List<String> missedKeywords;
        public String error;
    }

    /**
     * 聚合评估报告
     */
    public static class AgentEvalReport {
        public int totalCases;
        public int toolCorrectCount;
        public int taskCompleteCount;
        public double toolAccuracy;
        public double taskCompletionRate;
        public List<AgentEvalResult> details;
    }

    /**
     * 执行 Agent 评估
     */
    public AgentEvalReport evaluate(List<AgentEvalCase> cases, String userId) {
        log.info("Starting Agent evaluation: {} cases", cases.size());

        List<AgentEvalResult> results = new ArrayList<>();

        for (AgentEvalCase evalCase : cases) {
            AgentEvalResult result = evaluateSingle(evalCase, userId);
            results.add(result);
        }

        // 聚合
        AgentEvalReport report = new AgentEvalReport();
        report.totalCases = cases.size();
        report.details = results;
        report.toolCorrectCount = (int) results.stream().filter(r -> r.toolCorrect).count();
        report.taskCompleteCount = (int) results.stream().filter(r -> r.taskComplete).count();
        report.toolAccuracy = cases.isEmpty() ? 0 : (double) report.toolCorrectCount / cases.size();
        report.taskCompletionRate = cases.isEmpty() ? 0 : (double) report.taskCompleteCount / cases.size();

        log.info("Agent evaluation complete: toolAccuracy={}%, taskCompletion={}%",
                String.format("%.2f", report.toolAccuracy * 100),
                String.format("%.2f", report.taskCompletionRate * 100));

        return report;
    }

    private AgentEvalResult evaluateSingle(AgentEvalCase evalCase, String userId) {
        AgentEvalResult result = new AgentEvalResult();
        result.userQuery = evalCase.userQuery;
        result.expectedTool = evalCase.expectedToolName;
        result.expectedAction = evalCase.expectedAction;
        result.matchedKeywords = new ArrayList<>();
        result.missedKeywords = new ArrayList<>();

        try {
            // 通过 Agent 执行
            AiChatResponse response = agentService.chat(evalCase.userQuery, List.of(), userId);
            result.actualResponse = response.getContent();

            // 1. 工具调用准确率判定
            // 检查响应内容是否包含工具执行的痕迹
            result.toolCorrect = checkToolUsage(result.actualResponse, evalCase);

            // 2. E2E 任务完成率判定
            // 检查响应是否包含期望的关键词
            if (evalCase.expectedResponseKeywords != null) {
                for (String keyword : evalCase.expectedResponseKeywords) {
                    if (result.actualResponse != null &&
                            result.actualResponse.toLowerCase().contains(keyword.toLowerCase())) {
                        result.matchedKeywords.add(keyword);
                    } else {
                        result.missedKeywords.add(keyword);
                    }
                }
            }

            // 任务完成 = 工具正确 + 所有关键词命中
            result.taskComplete = result.toolCorrect && result.missedKeywords.isEmpty();

            // 如果期望成功但响应包含错误信息
            if (evalCase.expectSuccess && result.actualResponse != null) {
                if (result.actualResponse.contains("失败") || result.actualResponse.contains("出错")) {
                    result.taskComplete = false;
                }
            }

        } catch (Exception e) {
            log.warn("Agent evaluation failed for query '{}': {}", evalCase.userQuery, e.getMessage());
            result.error = e.getMessage();
            result.toolCorrect = false;
            result.taskComplete = false;
        }

        return result;
    }

    /**
     * 检查 Agent 是否使用了正确的工具
     * 基于响应内容的启发式判断
     */
    private boolean checkToolUsage(String response, AgentEvalCase evalCase) {
        if (response == null || evalCase.expectedAction == null) return false;

        // 根据期望的 action，检查响应中是否有对应的操作结果
        return switch (evalCase.expectedAction) {
            case "create" -> containsAny(response, "已创建", "创建成功", "已为您创建");
            case "search", "listAll" -> containsAny(response, "找到", "搜索结果", "以下是", "共有", "条笔记");
            case "delete" -> containsAny(response, "已删除", "删除成功", "确认删除", "PENDING_ACTION");
            case "update" -> containsAny(response, "已更新", "更新成功", "已修改");
            case "addTag" -> containsAny(response, "已添加标签", "标签");
            case "removeTag" -> containsAny(response, "已移除标签", "已从");
            case "list" -> containsAny(response, "日程", "以下是", "共有");
            default -> {
                // 通用检查：响应不是错误消息
                yield !containsAny(response, "未知操作", "操作失败", "抱歉");
            }
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
