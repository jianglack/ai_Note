package com.ainote.app.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import org.springframework.stereotype.Component;

@Component
public class JiTokenCountEstimator implements TokenCountEstimator {

    private static final int PER_MESSAGE_OVERHEAD = 4;

    private final JiTokenService jiTokenService;

    public JiTokenCountEstimator(JiTokenService jiTokenService) {
        this.jiTokenService = jiTokenService;
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return jiTokenService.countTokens(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        String text;
        if (message instanceof UserMessage userMessage) {
            if (userMessage.hasSingleText()) {
                text = userMessage.singleText();
            } else {
                text = userMessage.contents() == null ? "" : userMessage.contents().toString();
            }
        } else if (message instanceof AiMessage aiMessage) {
            StringBuilder sb = new StringBuilder();
            if (aiMessage.text() != null) {
                sb.append(aiMessage.text());
            }
            if (aiMessage.toolExecutionRequests() != null) {
                for (var request : aiMessage.toolExecutionRequests()) {
                    sb.append(request.name()).append(request.arguments());
                }
            }
            text = sb.toString();
        } else if (message instanceof SystemMessage systemMessage) {
            text = systemMessage.text();
        } else if (message instanceof ToolExecutionResultMessage toolResult) {
            text = toolResult.toolName() + " " + toolResult.text();
        } else {
            text = "";
        }
        return jiTokenService.countTokens(text) + PER_MESSAGE_OVERHEAD;
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokenCountInMessage(message);
        }
        return total;
    }
}
