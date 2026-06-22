package com.ainote.ai.config;

import com.ainote.ai.agent.AgentAssistant;
import com.ainote.ai.agent.pipeline.GracefulDegradation;
import com.ainote.ai.agent.tools.FolderActionTool;
import com.ainote.ai.agent.tools.NoteActionTool;
import com.ainote.ai.agent.tools.ScheduleActionTool;
import com.ainote.ai.memory.ReliableChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * 工具调用计数器（线程级），仅用于 beforeToolExecution 日志和优雅降级标记。
     */
    public static final ThreadLocal<AtomicInteger> TOOL_CALL_COUNTER =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));

    @Value("${app.chat.max-memory-tokens:8000}")
    private int maxMemoryTokens;

    @Value("${app.agent.max-tool-invocations:30}")
    private int maxToolInvocations;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ReliableChatMemoryStore store) {
        log.info("Creating ChatMemoryProvider with maxTokens: {}", maxMemoryTokens);
        return memoryId -> TokenWindowChatMemory.builder()
                // Local token estimator only; no OpenAI API call is made.
                .maxTokens(maxMemoryTokens, new OpenAiTokenCountEstimator("gpt-4"))
                .chatMemoryStore(store)
                .id(memoryId)
                .build();
    }

    @Bean
    public AgentAssistant agentAssistant(
            @Qualifier("agentChatModel") ChatModel agentChatModel,
            NoteActionTool noteActionTool,
            ScheduleActionTool scheduleActionTool,
            FolderActionTool folderActionTool,
            ChatMemoryProvider chatMemoryProvider
    ) {
        final int maxInvocations = maxToolInvocations;
        log.info("Creating AgentAssistant with maxToolInvocations: {}", maxInvocations);

        return AiServices.builder(AgentAssistant.class)
                .chatModel(agentChatModel)
                .tools(noteActionTool, scheduleActionTool, folderActionTool)
                .chatMemoryProvider(chatMemoryProvider)
                .maxSequentialToolsInvocations(maxInvocations)
                .beforeToolExecution(context -> {
                    int count = TOOL_CALL_COUNTER.get().incrementAndGet();
                    log.info("Tool call #{}/{}: {}", count, maxInvocations, context.request().name());
                    if (count >= maxInvocations - 2) {
                        log.warn("Approaching tool call limit ({}/{}), agent should wrap up",
                                count, maxInvocations);
                        GracefulDegradation.markApproachingLimit();
                    }
                })
                .build();
    }

    @Bean("securityExecutor")
    public ExecutorService securityExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
