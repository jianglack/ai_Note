package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskStep;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);
    private static final Pattern HARD_ERROR_PATTERN = Pattern.compile(
            ".*(\u5931\u8d25|\u65e0\u6743\u8bbf\u95ee).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public enum Decision { CONTINUE, RETRY, REPLAN, INSERT_STEP }

    private final ChatModel chatModel;

    public ReflectionService(@Qualifier("agentChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Decide what to do after a step execution.
     * Rules-first approach: assume success unless there's strong evidence of failure.
     * The agent uses tools and returns natural language — we should trust non-empty responses.
     */
    public Decision reflect(TaskStep step, String agentResponse, Exception error) {
        // Rule 1: Exception → check if retryable
        if (error != null) {
            String msg = error.getMessage() != null ? error.getMessage() : "";
            // API balance/quota errors should fail immediately, no point retrying
            if (isQuotaError(msg)) {
                log.error("Step {} hit API quota limit, decision=REPLAN (will fail): {}", step.getStepOrder(), msg);
                return Decision.REPLAN;
            }
            log.info("Step {} threw exception, decision=RETRY: {}", step.getStepOrder(), msg);
            if (step.getRetryCount() >= step.getMaxRetries()) {
                return Decision.REPLAN;
            }
            return Decision.RETRY;
        }

        // Rule 2: Empty response → RETRY
        if (agentResponse == null || agentResponse.isBlank()) {
            log.info("Step {} returned empty response, decision=RETRY", step.getStepOrder());
            if (step.getRetryCount() >= step.getMaxRetries()) return Decision.REPLAN;
            return Decision.RETRY;
        }

        // Rule 3: Only flag hard failure patterns — API errors, explicit execution failures
        // Do NOT flag common Chinese words like 无法/找不到 which appear in normal successful responses
        if (containsHardErrorIndicators(agentResponse)) {
            log.info("Step {} response has hard error indicators, decision=RETRY", step.getStepOrder());
            if (step.getRetryCount() >= step.getMaxRetries()) return Decision.REPLAN;
            return Decision.RETRY;
        }

        // Rule 3.5: Detect prerequisite missing — suggest inserting a step
        if (containsPrerequisiteHint(agentResponse)) {
            log.info("Step {} response suggests a prerequisite is needed, decision=INSERT_STEP", step.getStepOrder());
            return Decision.INSERT_STEP;
        }

        // Rule 4: Non-empty response from agent → trust it as success
        log.info("Step {} succeeded (response length={}), decision=CONTINUE", step.getStepOrder(), agentResponse.length());
        return Decision.CONTINUE;
    }

    /**
     * LLM-based reflection for ambiguous cases.
     */
    public Decision reflectWithLlm(TaskStep step, String agentResponse, String expectedOutcome) {
        String prompt = String.format(
                "你是一个任务执行评估器。判断以下步骤的执行结果是否符合预期。\n\n" +
                "步骤描述: %s\n期望结果: %s\n实际结果: %s\n\n" +
                "请回答: CONTINUE（结果符合预期）/ RETRY（结果不对，重试）/ REPLAN（需要重新规划）\n" +
                "只输出一个词。",
                step.getDescription(), expectedOutcome, agentResponse);

        try {
            var chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build();
            var response = chatModel.chat(chatRequest);
            String decision = response.aiMessage().text().trim().toUpperCase();
            if (decision.contains("RETRY")) return Decision.RETRY;
            if (decision.contains("REPLAN")) return Decision.REPLAN;
            return Decision.CONTINUE;
        } catch (Exception e) {
            log.warn("LLM reflection failed, defaulting to RETRY: {}", e.getMessage());
            return Decision.RETRY;
        }
    }

    /**
     * Only detect hard failure patterns that clearly indicate the step did NOT execute.
     * Normal agent responses often contain words like 无法/找不到/不存在 in successful contexts
     * (e.g., "找不到更多相关笔记" is a valid search result, not a failure).
     */
    private boolean containsHardErrorIndicators(String response) {
        String lower = response.toLowerCase();
        if (HARD_ERROR_PATTERN.matcher(response).matches()) {
            return true;
        }
        // API/system-level errors (not natural language)
        return lower.contains("余额不足") || lower.contains("请充值")
                || lower.contains("api key") || lower.contains("rate limit")
                || lower.contains("quota exceeded") || lower.contains("insufficient_quota")
                || lower.contains("java.lang.") || lower.contains("stacktrace")
                || lower.contains("500 internal server error")
                || lower.contains("连接超时") || lower.contains("connection refused")
                || lower.contains("缺少 ")
                || lower.contains("参数格式错误")
                || lower.contains("未知操作")
                || lower.contains("操作失败")
                || lower.contains("创建失败")
                || lower.contains("更新失败")
                || lower.contains("无法解析开始时间");
    }

    /**
     * 检测响应中是否暗示需要前置操作（如文件夹不存在需要先创建）
     */
    private boolean containsPrerequisiteHint(String response) {
        String lower = response.toLowerCase();
        return (lower.contains("文件夹不存在") && lower.contains("创建"))
                || (lower.contains("找不到指定的") && lower.contains("请先"))
                || (lower.contains("需要先") && (lower.contains("创建") || lower.contains("设置")))
                || lower.contains("prerequisite") || lower.contains("must first create");
    }

    private boolean isQuotaError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("余额不足") || lower.contains("请充值")
                || lower.contains("quota") || lower.contains("insufficient")
                || lower.contains("rate limit") || lower.contains("429");
    }
}
