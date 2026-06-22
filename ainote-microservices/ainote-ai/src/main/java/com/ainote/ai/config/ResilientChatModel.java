package com.ainote.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 熔断装饰器：包装 ChatModel，主模型失败时自动降级到备用模型
 */
public class ResilientChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(ResilientChatModel.class);

    private final ChatModel primary;
    private final ChatModel fallback;
    private final String name;

    public ResilientChatModel(ChatModel primary, ChatModel fallback, String name) {
        this.primary = primary;
        this.fallback = fallback;
        this.name = name;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        try {
            return primary.chat(chatRequest);
        } catch (Exception e) {
            if (fallback != null) {
                log.warn("[{}] Primary model failed, falling back: {}", name, e.getMessage());
                return fallback.chat(chatRequest);
            }
            throw e;
        }
    }
}
