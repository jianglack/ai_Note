package com.ainote.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AgentAssistant {

    @SystemMessage(fromResource = "prompts/agent-system.txt")
    String chat(
            @MemoryId String memoryId,
            @UserMessage String userMessage,
            @V("currentTime") String currentTime,
            @V("noteContext") String noteContext
    );
}
