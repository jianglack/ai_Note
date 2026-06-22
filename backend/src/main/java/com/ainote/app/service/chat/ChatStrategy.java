package com.ainote.app.service.chat;

import com.ainote.app.model.AiChatResponse;

import java.util.List;

/**
 * Common contract for chat execution paths.
 * chatStream must block until callback lifecycle is complete.
 */
public interface ChatStrategy {

    AiChatResponse chat(String query, List<String> noteIds, String userId);

    void chatStream(String query, List<String> noteIds, String userId, StreamCallback callback);

    String name();
}
