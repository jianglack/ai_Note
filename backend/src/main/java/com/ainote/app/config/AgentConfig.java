package com.ainote.app.config;

import com.ainote.app.agent.AgentAssistant;
import com.ainote.app.agent.pipeline.GracefulDegradation;
import com.ainote.app.agent.tools.FolderActionTool;
import com.ainote.app.agent.tools.InsightActionTool;
import com.ainote.app.agent.tools.KnowledgeActionTool;
import com.ainote.app.agent.tools.MediaActionTool;
import com.ainote.app.agent.tools.NoteActionTool;
import com.ainote.app.agent.tools.ScheduleActionTool;
import com.ainote.app.agent.tools.WebSearchTool;
import com.ainote.app.memory.ReliableChatMemoryStore;
import com.ainote.app.service.JiTokenCountEstimator;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 配置类
 * 配置 LangChain4j AiServices 和相关组件
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * 工具调用计数器（线程级），仅用于 beforeToolExecution 日志和优雅降级标记。
     * Retry 由 ResilientChatModel 处理，循环检测由 PreExecutionGuard 处理。
     */
    public static final ThreadLocal<AtomicInteger> TOOL_CALL_COUNTER =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));

    @Value("${app.chat.max-memory-tokens:8000}")
    private int maxMemoryTokens;

    @Value("${app.agent.max-tool-invocations:30}")
    private int maxToolInvocations;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(
            ReliableChatMemoryStore store,
            JiTokenCountEstimator tokenEstimator) {
        log.info("Creating ChatMemoryProvider with ReliableChatMemoryStore, maxTokens: {}", maxMemoryTokens);
        return memoryId -> {
            log.debug("Creating TokenWindowChatMemory for user: {}", memoryId);
            return TokenWindowChatMemory.builder()
                    .maxTokens(maxMemoryTokens, tokenEstimator)
                    .chatMemoryStore(store)
                    .id(memoryId)
                    .build();
        };
    }

    @Bean
    public AgentAssistant agentAssistant(
            @Qualifier("agentChatModel") ChatModel agentChatModel,
            NoteActionTool noteActionTool,
            ScheduleActionTool scheduleActionTool,
            FolderActionTool folderActionTool,
            KnowledgeActionTool knowledgeActionTool,
            InsightActionTool insightActionTool,
            MediaActionTool mediaActionTool,
            WebSearchTool webSearchTool,
            ChatMemoryProvider chatMemoryProvider
    ) {
        final int maxInvocations = maxToolInvocations;
        log.info("Creating AgentAssistant with dedicated agent model, maxToolInvocations: {}", maxInvocations);

        return AiServices.builder(AgentAssistant.class)
                .chatModel(agentChatModel)
                .tools(noteActionTool, scheduleActionTool, folderActionTool,
                       knowledgeActionTool, insightActionTool, mediaActionTool, webSearchTool)
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
}
