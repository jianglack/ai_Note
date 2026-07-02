package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.entity.Note;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.service.ContextAssembler;
import com.ainote.app.util.PromptLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadOnlyStreamingChatServiceTest {

    private StreamingChatModel streamingChatModel;
    private ContextAssembler contextAssembler;
    private NoteRepository noteRepository;
    private OutputGuardrail outputGuardrail;
    private PromptLoader promptLoader;
    private ReadOnlyStreamingChatService service;

    @BeforeEach
    void setUp() {
        streamingChatModel = Mockito.mock(StreamingChatModel.class);
        contextAssembler = Mockito.mock(ContextAssembler.class);
        noteRepository = Mockito.mock(NoteRepository.class);
        outputGuardrail = Mockito.mock(OutputGuardrail.class);
        promptLoader = Mockito.mock(PromptLoader.class);

        when(promptLoader.load("fallback-system.txt")).thenReturn("You are helpful.");
        when(contextAssembler.assemble(anyString(), any(), anyString())).thenReturn("context");
        when(outputGuardrail.sanitize(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        service = new ReadOnlyStreamingChatService(
                streamingChatModel,
                contextAssembler,
                noteRepository,
                outputGuardrail,
                promptLoader,
                true,
                32,
                1);
    }

    @Test
    void canHandleOnlyReadOnlyRequests() {
        assertThat(service.canHandle("summarize my notes", List.of())).isTrue();
        assertThat(service.canHandle("delete current note", List.of("n1"))).isFalse();
        assertThat(service.canHandle("\u5220\u9664\u5f53\u524d\u7b14\u8bb0", List.of("n1"))).isFalse();
        assertThat(service.canHandle("  ", List.of())).isFalse();
    }

    @Test
    void streamsPartialTokensAndCompletesWithSources() {
        Note note = new Note();
        note.setId("n1");
        note.setTitle("Note One");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("n1", "u1")).thenReturn(Optional.of(note));
        Mockito.doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("he");
            handler.onPartialResponse("llo");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("hello"))
                    .build());
            return null;
        }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        CapturingCallback callback = new CapturingCallback();

        boolean handled = service.chatStream("summarize", List.of("n1"), "u1", callback);

        assertThat(handled).isTrue();
        assertThat(callback.tokens).hasToString("hello");
        assertThat(callback.complete.getContent()).isEqualTo("hello");
        assertThat(callback.complete.getChatMode()).isEqualTo("DIRECT_STREAM");
        assertThat(callback.complete.getSources()).containsKey(1);
        verify(outputGuardrail).sanitize("hello", "u1");
    }

    @Test
    void skipsContextAssemblyWhenNoNotesAreSelected() {
        Mockito.doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("ok"))
                    .build());
            return null;
        }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        CapturingCallback callback = new CapturingCallback();

        boolean handled = service.chatStream("summarize", List.of(), "u1", callback);

        assertThat(handled).isTrue();
        assertThat(callback.complete.getContent()).isEqualTo("ok");
        verify(contextAssembler, never()).assemble(anyString(), any(), anyString());
    }

    @Test
    void returnsFalseForPreTokenStreamingFailureSoCallerCanFallback() {
        Mockito.doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("provider down"));
            return null;
        }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        CapturingCallback callback = new CapturingCallback();

        boolean handled = service.chatStream("summarize", List.of(), "u1", callback);

        assertThat(handled).isFalse();
        assertThat(callback.error).isNull();
    }

    private static class CapturingCallback implements StreamCallback {
        private final StringBuilder tokens = new StringBuilder();
        private AiChatResponse complete;
        private String error;

        @Override
        public void onToken(String token) {
            tokens.append(token);
        }

        @Override
        public void onComplete(AiChatResponse response) {
            complete = response;
        }

        @Override
        public void onError(String errorMessage) {
            error = errorMessage;
        }

        @Override
        public void onProgress(String step, String detail) {
        }
    }
}
