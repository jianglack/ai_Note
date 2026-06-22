package com.ainote.app.service.chat;

import com.ainote.app.model.AiChatResponse;

public interface StreamCallback {
    void onToken(String token);

    void onProgress(String step, String detail);

    void onComplete(AiChatResponse response);

    void onError(String errorMessage);
}
