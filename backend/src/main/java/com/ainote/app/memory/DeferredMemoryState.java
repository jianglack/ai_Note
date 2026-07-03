package com.ainote.app.memory;

import dev.langchain4j.data.message.ChatMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeferredMemoryState {

    private static final ThreadLocal<DeferredMemoryState> CURRENT = new ThreadLocal<>();

    private final Map<String, List<ChatMessage>> pendingWrites = new LinkedHashMap<>();

    public static void begin() {
        CURRENT.set(new DeferredMemoryState());
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static void buffer(String memoryId, List<ChatMessage> messages) {
        DeferredMemoryState state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException("DeferredMemoryState not active");
        }
        state.pendingWrites.put(memoryId, List.copyOf(messages));
    }

    public static List<ChatMessage> peek(String memoryId) {
        DeferredMemoryState state = CURRENT.get();
        if (state == null) {
            return null;
        }
        List<ChatMessage> messages = state.pendingWrites.get(memoryId);
        return messages == null ? null : List.copyOf(messages);
    }

    public static Map<String, List<ChatMessage>> drain() {
        DeferredMemoryState state = CURRENT.get();
        if (state == null) {
            return Map.of();
        }
        Map<String, List<ChatMessage>> result = new LinkedHashMap<>(state.pendingWrites);
        state.pendingWrites.clear();
        return result;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
