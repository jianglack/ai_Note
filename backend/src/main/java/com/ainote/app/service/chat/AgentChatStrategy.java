package com.ainote.app.service.chat;

import com.ainote.app.model.AiChatResponse;
import com.ainote.app.service.AgentService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

@Component
public class AgentChatStrategy implements ChatStrategy {

    private static final Set<String> FAILURE_KEYWORDS = Set.of(
            "AGENT_TIMEOUT",
            "AGENT_FAILED",
            "AGENT_ERROR",
            "\u5904\u7406\u65f6\u95f4\u8fc7\u957f",
            "\u8bf7\u7a0d\u540e\u518d\u8bd5",
            "\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528",
            "\u5185\u90e8\u9519\u8bef"
    );
    private static final Set<String> REJECTION_KEYWORDS = Set.of(
            "AGENT_BUSY",
            "AI agent is busy",
            "REQUEST_REJECTED_BY_GUARDRAIL",
            "\u68c0\u6d4b\u5230\u4e0d\u5b89\u5168",
            "\u8bf7\u52ff\u8f93\u5165",
            "\u65e0\u6cd5\u6267\u884c\u8be5\u64cd\u4f5c",
            "\u6b63\u5728\u8fdb\u884c",
            "\u7b49\u5f85\u5b8c\u6210"
    );

    private final AgentService agentService;

    public AgentChatStrategy(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public AiChatResponse chat(String query, List<String> noteIds, String userId) {
        AiChatResponse response = agentService.chatAlreadyChecked(query, noteIds, userId);
        if (looksLikeFailure(response)) {
            throw new ChatStrategyException(
                    "AGENT_FAILED",
                    "Agent returned error response",
                    response != null ? response.getContent() : null);
        }
        return response;
    }

    @Override
    public void chatStream(String query, List<String> noteIds,
                           String userId, StreamCallback callback) {
        chatStream(query, noteIds, userId, java.util.UUID.randomUUID().toString(), callback);
    }

    @Override
    public void chatStream(String query, List<String> noteIds,
                           String userId, String requestId, StreamCallback callback) {
        agentService.chatStreamAlreadyChecked(query, noteIds, userId, requestId,
                new AgentService.StreamCallback() {
                    @Override
                    public void onToken(String token) {
                        callback.onToken(token);
                    }

                    @Override
                    public void onComplete(AiChatResponse response) {
                        if (looksLikeFailure(response)) {
                            throw new ChatStrategyException(
                                    "AGENT_STREAM_FAILED",
                                    "Agent stream returned error response",
                                    response != null ? response.getContent() : null);
                        }
                        callback.onComplete(response);
                    }

                    @Override
                    public void onError(String error) {
                        if (looksLikeRejection(error)) {
                            callback.onToken(error);
                            AiChatResponse response = new AiChatResponse(error, new HashMap<>());
                            response.setChatMode("REJECTED");
                            response.setDegraded(false);
                            callback.onComplete(response);
                            return;
                        }
                        throw new ChatStrategyException("AGENT_STREAM_ERROR", error, null);
                    }

                    @Override
                    public void onProgress(String step, String detail) {
                        callback.onProgress(step, detail);
                    }
                });
    }

    @Override
    public String name() {
        return "AGENT";
    }

    private boolean looksLikeFailure(AiChatResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            return true;
        }
        String content = response.getContent();
        if (looksLikeRejection(content)) {
            return false;
        }
        return FAILURE_KEYWORDS.stream().anyMatch(content::contains);
    }

    private boolean looksLikeRejection(String content) {
        return content != null && REJECTION_KEYWORDS.stream().anyMatch(content::contains);
    }
}
