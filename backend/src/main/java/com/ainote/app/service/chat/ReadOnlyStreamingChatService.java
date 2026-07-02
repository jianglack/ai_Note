package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.service.ContextAssembler;
import com.ainote.app.util.PromptLoader;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ReadOnlyStreamingChatService {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyStreamingChatService.class);

    private static final List<String> AGENT_REQUIRED_KEYWORDS = List.of(
            "delete",
            "remove",
            "trash",
            "clear trash",
            "empty trash",
            "permanent",
            "create",
            "add",
            "update",
            "edit",
            "move",
            "archive",
            "restore",
            "tag",
            "schedule",
            "calendar",
            "extract",
            "workflow",
            "run",
            "\u5220\u9664",
            "\u79fb\u9664",
            "\u6e05\u7a7a",
            "\u56de\u6536\u7ad9",
            "\u6c38\u4e45",
            "\u521b\u5efa",
            "\u65b0\u5efa",
            "\u6dfb\u52a0",
            "\u4fee\u6539",
            "\u7f16\u8f91",
            "\u79fb\u52a8",
            "\u5f52\u6863",
            "\u6062\u590d",
            "\u6807\u7b7e",
            "\u65e5\u7a0b",
            "\u63d0\u53d6",
            "\u5de5\u4f5c\u6d41",
            "\u6267\u884c"
    );

    private final StreamingChatModel streamingChatModel;
    private final ContextAssembler contextAssembler;
    private final NoteRepository noteRepository;
    private final OutputGuardrail outputGuardrail;
    private final PromptLoader promptLoader;
    private final boolean enabled;
    private final int maxOutputTokens;
    private final int timeoutSeconds;

    public ReadOnlyStreamingChatService(
            StreamingChatModel streamingChatModel,
            ContextAssembler contextAssembler,
            NoteRepository noteRepository,
            OutputGuardrail outputGuardrail,
            PromptLoader promptLoader,
            @Value("${app.chat.direct-stream.enabled:true}") boolean enabled,
            @Value("${app.chat.direct-stream.max-tokens:512}") int maxOutputTokens,
            @Value("${app.chat.direct-stream.timeout-seconds:120}") int timeoutSeconds) {
        this.streamingChatModel = streamingChatModel;
        this.contextAssembler = contextAssembler;
        this.noteRepository = noteRepository;
        this.outputGuardrail = outputGuardrail;
        this.promptLoader = promptLoader;
        this.enabled = enabled;
        this.maxOutputTokens = Math.max(1, maxOutputTokens);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public boolean canHandle(String query, List<String> noteIds) {
        if (!enabled || query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return AGENT_REQUIRED_KEYWORDS.stream().noneMatch(normalized::contains);
    }

    public boolean chatStream(String query, List<String> noteIds, String userId, StreamCallback callback) {
        CountDownLatch done = new CountDownLatch(1);
        StringBuilder streamedContent = new StringBuilder();
        AtomicBoolean emittedTokens = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<AiChatResponse> completeResponse = new AtomicReference<>();

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(buildSystemPrompt()),
                        UserMessage.from(buildUserPrompt(query, noteIds, userId)))
                .maxOutputTokens(maxOutputTokens)
                .build();

        try {
            streamingChatModel.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse == null || partialResponse.isEmpty()) {
                        return;
                    }
                    emittedTokens.set(true);
                    streamedContent.append(partialResponse);
                    callback.onToken(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    String content = streamedContent.toString();
                    if (content.isBlank() && response != null && response.aiMessage() != null) {
                        content = Optional.ofNullable(response.aiMessage().text()).orElse("");
                    }
                    try {
                        content = outputGuardrail.sanitize(content, userId);
                    } catch (Exception e) {
                        log.warn("Output guardrail failed in direct stream", e);
                        content = "Sorry, the response could not be safely returned.";
                    }
                    AiChatResponse aiResponse = new AiChatResponse(content, buildNoteSources(noteIds, userId));
                    aiResponse.setChatMode("DIRECT_STREAM");
                    aiResponse.setDegraded(false);
                    completeResponse.set(aiResponse);
                    done.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    error.set(throwable);
                    done.countDown();
                }
            });
        } catch (RuntimeException e) {
            return failOrFallback(callback, emittedTokens.get(), "AI stream failed: " + e.getMessage());
        }

        try {
            if (!done.await(timeoutSeconds, TimeUnit.SECONDS)) {
                return failOrFallback(callback, emittedTokens.get(), "AI stream timed out, please retry shortly.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failOrFallback(callback, emittedTokens.get(), "AI stream was interrupted.");
        }

        Throwable streamError = error.get();
        if (streamError != null) {
            return failOrFallback(callback, emittedTokens.get(), "AI stream failed: " + streamError.getMessage());
        }
        callback.onComplete(completeResponse.get());
        return true;
    }

    private String buildSystemPrompt() {
        String fallbackPrompt = promptLoader.load("fallback-system.txt");
        return fallbackPrompt + "\n\n"
                + "You are in read-only streaming mode. Answer the user's question directly and concisely. "
                + "Do not claim that you created, edited, deleted, moved, archived, restored, scheduled, "
                + "or otherwise changed application data.";
    }

    private String buildUserPrompt(String query, List<String> noteIds, String userId) {
        StringBuilder prompt = new StringBuilder();
        String context = assembleContext(query, noteIds, userId);
        if (context != null && !context.isBlank()) {
            prompt.append("Context:\n").append(context).append("\n\n");
        }
        prompt.append("User question:\n").append(query);
        return prompt.toString();
    }

    private String assembleContext(String query, List<String> noteIds, String userId) {
        if (noteIds == null || noteIds.isEmpty()) {
            return "";
        }
        try {
            return contextAssembler.assemble(query, noteIds, userId);
        } catch (Exception e) {
            log.warn("Direct stream context assembly failed; continuing without context", e);
            return "";
        }
    }

    private Map<Integer, AiChatResponse.NoteSource> buildNoteSources(List<String> noteIds, String userId) {
        Map<Integer, AiChatResponse.NoteSource> sources = new HashMap<>();
        if (noteIds == null || noteIds.isEmpty()) {
            return sources;
        }

        for (String noteId : noteIds) {
            var note = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
            if (note.isPresent()) {
                sources.put(sources.size() + 1,
                        new AiChatResponse.NoteSource(note.get().getId(), note.get().getTitle()));
            }
        }
        return sources;
    }

    private boolean failOrFallback(StreamCallback callback, boolean emittedTokens, String message) {
        if (emittedTokens) {
            callback.onError(message);
            return true;
        }
        return false;
    }
}
