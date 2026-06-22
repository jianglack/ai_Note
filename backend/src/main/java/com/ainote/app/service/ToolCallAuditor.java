package com.ainote.app.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 工具调用审计器
 * 后置验证：比对 AI 回复中的操作声明与实际工具调用结果
 *
 * 这是 best-effort 安全网，主防线是 system prompt + ReliableChatMemoryStore。
 * 已知局限：
 * - 漏报：AI 用未收录的措辞声称完成操作
 * - 误报：AI 引用用户文本或举例时包含 UUID
 */
@Service
public class ToolCallAuditor {

    private static final Logger log = LoggerFactory.getLogger(ToolCallAuditor.class);

    /**
     * 可扩展的操作声明关键词集合
     */
    private static final Set<String> ACTION_CLAIM_KEYWORDS = Set.of(
            "已创建", "已删除", "已更新", "已保存",
            "操作完成", "已为您", "创建成功", "删除成功", "更新成功"
    );

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-");

    /**
     * 验证 AI 回复是否与实际工具调用一致。
     *
     * 并发假设：single-user-single-session。
     */
    public String validate(String aiResponse, String memoryId,
                           ChatMemoryStore memoryStore, int preCallMessageCount) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return aiResponse;
        }

        try {
            List<ChatMessage> allMessages = memoryStore.getMessages(memoryId);

            if (allMessages.size() <= preCallMessageCount) {
                return aiResponse;
            }

            List<ChatMessage> newMessages = allMessages.subList(
                    preCallMessageCount, allMessages.size());

            boolean hasToolCalls = newMessages.stream()
                    .anyMatch(m -> m instanceof ToolExecutionResultMessage);

            boolean claimsAction = containsActionClaim(aiResponse);

            if (claimsAction && !hasToolCalls) {
                log.warn("Hallucination detected: AI claims action but no tool calls found. Response: {}",
                        aiResponse.length() > 200 ? aiResponse.substring(0, 200) + "..." : aiResponse);
                return buildCorrectionResponse();
            }

            return aiResponse;

        } catch (Exception e) {
            log.error("ToolCallAuditor validation failed, returning original response", e);
            return aiResponse;
        }
    }

    private boolean containsActionClaim(String response) {
        String[] sentences = response.split("[。！？\\n]");
        for (String sentence : sentences) {
            boolean hasKeyword = ACTION_CLAIM_KEYWORDS.stream().anyMatch(sentence::contains);
            boolean hasUuid = UUID_PATTERN.matcher(sentence).find();
            if (hasKeyword && hasUuid) {
                return true;
            }
        }
        return false;
    }

    private String buildCorrectionResponse() {
        return "我注意到可能存在操作执行问题。请告诉我您希望执行什么操作，我会重新为您处理。";
    }
}
