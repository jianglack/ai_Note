package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.service.ContextAssembler;
import com.ainote.app.util.PromptLoader;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class ReadOnlyFallbackChatService implements ChatStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyFallbackChatService.class);

    private final ChatModel chatModel;
    private final ContextAssembler contextAssembler;
    private final NoteRepository noteRepository;
    private final OutputGuardrail outputGuardrail;
    private final PromptLoader promptLoader;
    private final ChatMetrics chatMetrics;

    public ReadOnlyFallbackChatService(
            @Qualifier("agentChatModel") ChatModel chatModel,
            ContextAssembler contextAssembler,
            NoteRepository noteRepository,
            OutputGuardrail outputGuardrail,
            PromptLoader promptLoader,
            ChatMetrics chatMetrics) {
        this.chatModel = chatModel;
        this.contextAssembler = contextAssembler;
        this.noteRepository = noteRepository;
        this.outputGuardrail = outputGuardrail;
        this.promptLoader = promptLoader;
        this.chatMetrics = chatMetrics;
    }

    @Override
    public String name() {
        return "FALLBACK";
    }

    @Override
    public AiChatResponse chat(String query, List<String> noteIds, String userId) {
        FallbackLevel level = FallbackLevel.LEVEL_1;
        String context;

        try {
            context = contextAssembler.assemble(query, noteIds, userId);
        } catch (Exception e) {
            log.warn("Fallback Level 1 failed (ContextAssembler), dropping to Level 2", e);
            level = FallbackLevel.LEVEL_2;
            try {
                context = loadSelectedNotes(noteIds, userId);
            } catch (Exception e2) {
                log.warn("Fallback Level 2 failed (note loading), dropping to Level 3", e2);
                level = FallbackLevel.LEVEL_3;
                context = null;
            }
        }

        String prompt = buildFallbackPrompt(query, context);
        String content = chatModel.chat(prompt);

        try {
            content = outputGuardrail.sanitize(content, userId);
        } catch (Exception e) {
            log.error("OutputGuardrail failed in fallback, returning safe message", e);
            content = "抱歉，回复生成过程中出现问题，请稍后再试。";
        }

        chatMetrics.recordFallbackLevel(level.name());

        AiChatResponse response = new AiChatResponse(content, new HashMap<>());
        response.setDegraded(true);
        response.setChatMode("FALLBACK");
        response.setDegradationReason(level.reason);
        return response;
    }

    @Override
    public void chatStream(String query, List<String> noteIds,
                           String userId, StreamCallback callback) {
        try {
            AiChatResponse response = chat(query, noteIds, userId);
            for (String segment : splitIntoSegments(response.getContent())) {
                callback.onToken(segment);
            }
            callback.onComplete(response);
        } catch (Exception e) {
            log.error("Fallback chatStream failed completely", e);
            callback.onError("抱歉，服务暂时不可用，请稍后再试");
        }
    }

    private String loadSelectedNotes(List<String> noteIds, String userId) {
        if (noteIds == null || noteIds.isEmpty()) {
            throw new IllegalStateException("No noteIds available for Level 2 fallback");
        }
        StringBuilder sb = new StringBuilder();
        for (String noteId : noteIds) {
            noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId)
                    .ifPresent(note -> {
                        sb.append("## ").append(note.getTitle()).append("\n");
                        sb.append(note.getContent()).append("\n\n");
                    });
        }
        if (sb.isEmpty()) {
            throw new IllegalStateException("No accessible notes found for given noteIds");
        }
        return sb.toString();
    }

    private String buildFallbackPrompt(String query, String context) {
        String systemPrompt = promptLoader.load("fallback-system.txt");
        StringBuilder prompt = new StringBuilder();
        prompt.append(systemPrompt).append("\n\n");
        if (context != null && !context.isBlank()) {
            prompt.append("以下是相关上下文信息：\n");
            prompt.append(context).append("\n\n");
        }
        prompt.append("用户问题：").append(query);
        return prompt.toString();
    }

    private List<String> splitIntoSegments(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return List.of(content.split("(?<=[。！？\\n])"));
    }

    enum FallbackLevel {
        LEVEL_1("Agent 暂时不可用，已使用完整上下文回复"),
        LEVEL_2("Agent 和上下文服务暂时不可用，已基于笔记内容回复"),
        LEVEL_3("服务部分不可用，已提供基础回复");

        final String reason;

        FallbackLevel(String reason) {
            this.reason = reason;
        }
    }
}
