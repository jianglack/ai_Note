package com.ainote.app.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DeferredMemoryState ThreadLocal buffer tests")
class DeferredMemoryStateTest {

    @AfterEach
    void cleanup() {
        DeferredMemoryState.clear();
    }

    @Test
    void beginBufferDrain() {
        DeferredMemoryState.begin();
        assertThat(DeferredMemoryState.isActive()).isTrue();

        List<ChatMessage> msgs1 = List.of(UserMessage.from("hello"));
        List<ChatMessage> msgs2 = List.of(UserMessage.from("world"));
        DeferredMemoryState.buffer("user-1", msgs1);
        DeferredMemoryState.buffer("user-2", msgs2);

        Map<String, List<ChatMessage>> drained = DeferredMemoryState.drain();
        assertThat(drained).hasSize(2);
        assertThat(((UserMessage) drained.get("user-1").get(0)).singleText()).isEqualTo("hello");
        assertThat(((UserMessage) drained.get("user-2").get(0)).singleText()).isEqualTo("world");
        assertThat(DeferredMemoryState.drain()).isEmpty();
    }

    @Test
    void bufferOverwritesSameKey() {
        DeferredMemoryState.begin();

        DeferredMemoryState.buffer("user-1", List.of(UserMessage.from("first")));
        DeferredMemoryState.buffer("user-1", List.of(UserMessage.from("second")));

        Map<String, List<ChatMessage>> drained = DeferredMemoryState.drain();
        assertThat(drained).hasSize(1);
        assertThat(((UserMessage) drained.get("user-1").get(0)).singleText()).isEqualTo("second");
    }

    @Test
    void bufferDefensiveCopy() {
        DeferredMemoryState.begin();

        List<ChatMessage> original = new ArrayList<>();
        original.add(UserMessage.from("hello"));
        DeferredMemoryState.buffer("user-1", original);

        original.add(UserMessage.from("injected"));

        Map<String, List<ChatMessage>> drained = DeferredMemoryState.drain();
        assertThat(drained.get("user-1")).hasSize(1);
    }

    @Test
    void clearRemovesThreadLocal() {
        DeferredMemoryState.begin();
        assertThat(DeferredMemoryState.isActive()).isTrue();

        DeferredMemoryState.clear();
        assertThat(DeferredMemoryState.isActive()).isFalse();
    }

    @Test
    void bufferWithoutBeginThrows() {
        assertThatThrownBy(() ->
                DeferredMemoryState.buffer("user-1", List.of(UserMessage.from("x"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void drainWithoutBeginReturnsEmpty() {
        assertThat(DeferredMemoryState.drain()).isEmpty();
    }
}
