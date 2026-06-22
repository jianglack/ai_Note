package com.ainote.app.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j AI Service 接口
 * 定义 Agent 与用户交互的方法
 *
 * LangChain4j 会自动：
 * 1. 调用配置的 ChatLanguageModel
 * 2. 执行 @Tool 注解的工具方法
 * 3. 将工具执行结果注入对话上下文
 * 4. 循环直到任务完成或达到最大迭代
 */
public interface AgentAssistant {

    @SystemMessage(fromResource = "prompts/agent-system.txt")
    String chat(
            @MemoryId String memoryId,
            @UserMessage String userMessage,
            @V("currentTime") String currentTime,
            @V("noteContext") String noteContext
    );
}
