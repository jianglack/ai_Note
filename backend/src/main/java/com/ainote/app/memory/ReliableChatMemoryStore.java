package com.ainote.app.memory;

import com.ainote.app.entity.UserMemory;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReliableChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(ReliableChatMemoryStore.class);

    private static final String TYPE_USER = "USER";
    private static final String TYPE_AI = "AI";
    private static final String TYPE_SYSTEM = "SYSTEM";
    private static final String TYPE_TOOL_EXECUTION = "TOOL_EXECUTION_RESULT";

    public static final ThreadLocal<Boolean> IN_AGENT_LOOP = ThreadLocal.withInitial(() -> false);

    private final UserMemoryRepository memoryRepository;
    @SuppressWarnings("unused")
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final ObjectMapper objectMapper;
    private final com.ainote.app.service.MemoryExtractionService memoryExtractionService;

    public ReliableChatMemoryStore(
            UserMemoryRepository memoryRepository,
            EpisodicMemoryRepository episodicMemoryRepository,
            ObjectMapper objectMapper,
            @Lazy com.ainote.app.service.MemoryExtractionService memoryExtractionService) {
        this.memoryRepository = memoryRepository;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.objectMapper = objectMapper;
        this.memoryExtractionService = memoryExtractionService;
        log.info("ReliableChatMemoryStore initialized (batch write + sequence_number + deferred flush)");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String userId = memoryId.toString();
        log.debug("Loading messages for user: {}", userId);

        try {
            List<ChatMessage> deferredMessages = DeferredMemoryState.peek(userId);
            if (deferredMessages != null) {
                if (!IN_AGENT_LOOP.get()) {
                    return sanitizeMessages(deferredMessages);
                }
                log.debug("Loaded {} deferred messages (in agent loop) for user: {}",
                        deferredMessages.size(), userId);
                return new ArrayList<>(deferredMessages);
            }

            List<UserMemory> memories = memoryRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
            List<ChatMessage> messages = memories.stream()
                    .map(this::toChatMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!IN_AGENT_LOOP.get()) {
                List<ChatMessage> sanitized = sanitizeMessages(messages);
                if (sanitized.size() != messages.size()) {
                    log.warn("Sanitized {} orphaned tool messages for user: {}",
                            messages.size() - sanitized.size(), userId);
                }
                log.debug("Loaded {} messages (sanitized) for user: {}", sanitized.size(), userId);
                return sanitized;
            }

            log.debug("Loaded {} messages (in agent loop, no sanitize) for user: {}", messages.size(), userId);
            return messages;
        } catch (Exception e) {
            log.error("Failed to load messages for user: {}", userId, e);
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String userId = memoryId.toString();
        log.debug("Updating messages for user: {}, count: {}", userId, messages.size());

        if (DeferredMemoryState.isActive()) {
            DeferredMemoryState.buffer(userId, messages);
            return;
        }

        try {
            doPersistMessages(userId, messages);
        } catch (Exception e) {
            log.error("Failed to update messages for user: {}", userId, e);
            throw new RuntimeException("Failed to update chat memory", e);
        }
    }

    private void doPersistMessages(String userId, List<ChatMessage> messages) {
        List<UserMemory> oldMemories = memoryRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        int oldUserMsgCount = (int) oldMemories.stream()
                .filter(m -> TYPE_USER.equals(m.getMessageType()))
                .count();
        int newUserMsgCount = (int) messages.stream()
                .filter(m -> m instanceof UserMessage)
                .count();

        if (oldUserMsgCount > newUserMsgCount && oldUserMsgCount >= 3) {
            triggerEpisodicSummaryForTrimmed(userId, oldMemories, messages);
        }

        memoryRepository.deleteByUserId(userId);

        List<UserMemory> batch = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            UserMemory memory = toUserMemory(userId, messages.get(i));
            if (memory != null) {
                memory.setSequenceNumber(i);
                batch.add(memory);
            }
        }

        memoryRepository.saveAll(batch);
        log.debug("Batch saved {} messages for user: {} (full rewrite)", batch.size(), userId);
    }

    @Transactional
    public void flushDeferredWrites() {
        Map<String, List<ChatMessage>> pending = DeferredMemoryState.drain();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Flushing {} deferred memory writes", pending.size());
        for (var entry : pending.entrySet()) {
            doPersistMessages(entry.getKey(), entry.getValue());
        }
    }

    private void triggerEpisodicSummaryForTrimmed(
            String userId,
            List<UserMemory> oldMemories,
            List<ChatMessage> newMessages) {
        try {
            String firstNewUserMsg = newMessages.stream()
                    .filter(m -> m instanceof UserMessage)
                    .map(m -> ((UserMessage) m).singleText())
                    .findFirst()
                    .orElse(null);

            if (firstNewUserMsg == null) {
                return;
            }

            List<UserMemory> trimmed = new ArrayList<>();
            for (UserMemory mem : oldMemories) {
                if (TYPE_USER.equals(mem.getMessageType()) && firstNewUserMsg.equals(mem.getContent())) {
                    break;
                }
                trimmed.add(mem);
            }

            if (trimmed.isEmpty()) {
                return;
            }

            String conversationText = trimmed.stream()
                    .filter(m -> TYPE_USER.equals(m.getMessageType()) || TYPE_AI.equals(m.getMessageType()))
                    .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                    .map(m -> (TYPE_USER.equals(m.getMessageType()) ? "User: " : "AI: ") + m.getContent())
                    .collect(Collectors.joining("\n"));

            if (conversationText.isBlank()) {
                return;
            }

            int trimmedUserCount = (int) trimmed.stream()
                    .filter(m -> TYPE_USER.equals(m.getMessageType()))
                    .count();

            log.info("TokenWindow trimmed {} messages ({} user turns) for user: {}, generating episodic summary",
                    trimmed.size(), trimmedUserCount, userId);
            memoryExtractionService.generateEpisodicSummaryFromText(userId, conversationText, trimmedUserCount);
        } catch (Exception e) {
            log.warn("Failed to trigger episodic summary for trimmed messages: {}", e.getMessage());
        }
    }

    private List<ChatMessage> sanitizeMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> declaredToolCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    if (req.id() != null) {
                        declaredToolCallIds.add(req.id());
                    }
                }
            }
        }

        Set<String> respondedToolCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage toolMsg && toolMsg.id() != null) {
                respondedToolCallIds.add(toolMsg.id());
            }
        }

        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                if (toolMsg.id() != null && declaredToolCallIds.contains(toolMsg.id())) {
                    result.add(msg);
                } else {
                    log.debug("Dropping orphaned tool result: id={}, toolName={}",
                            toolMsg.id(), toolMsg.toolName());
                }
            } else if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> requests = aiMsg.toolExecutionRequests();
                boolean allResponded = requests.stream()
                        .allMatch(req -> req.id() != null && respondedToolCallIds.contains(req.id()));

                if (allResponded) {
                    result.add(msg);
                } else {
                    String text = aiMsg.text();
                    if (text != null && !text.isBlank()) {
                        result.add(AiMessage.from(text));
                        log.warn("Stripped tool_calls from AiMessage (missing tool results): {} tool_calls removed",
                                requests.size());
                    } else {
                        log.warn("Dropping AiMessage with orphaned tool_calls (no text, {} tool_calls)",
                                requests.size());
                    }
                }
            } else {
                result.add(msg);
            }
        }

        return result;
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String userId = memoryId.toString();
        log.info("Deleting all messages for user: {}", userId);

        try {
            memoryRepository.deleteByUserId(userId);
        } catch (Exception e) {
            log.error("Failed to delete messages for user: {}", userId, e);
        }
    }

    private ChatMessage toChatMessage(UserMemory memory) {
        try {
            String type = memory.getMessageType();
            return switch (type) {
                case TYPE_USER -> UserMessage.from(memory.getContent());
                case TYPE_AI -> {
                    String toolCallsJson = memory.getToolCallsJson();
                    if (toolCallsJson != null && !toolCallsJson.isEmpty()) {
                        List<ToolExecutionRequest> requests = deserializeToolRequests(toolCallsJson);
                        yield AiMessage.from(memory.getContent(), requests);
                    }
                    yield AiMessage.from(memory.getContent());
                }
                case TYPE_SYSTEM -> SystemMessage.from(memory.getContent());
                case TYPE_TOOL_EXECUTION -> ToolExecutionResultMessage.from(
                        memory.getToolCallId(),
                        memory.getToolName(),
                        memory.getToolResult() != null ? memory.getToolResult() : ""
                );
                default -> {
                    log.warn("Unknown message type: {}", type);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn("Failed to convert UserMemory to ChatMessage: {}", e.getMessage());
            return null;
        }
    }

    private UserMemory toUserMemory(String userId, ChatMessage message) {
        try {
            UserMemory memory = new UserMemory();
            memory.setUserId(userId);

            if (message instanceof UserMessage userMsg) {
                memory.setMessageType(TYPE_USER);
                memory.setContent(userMsg.singleText());
            } else if (message instanceof AiMessage aiMsg) {
                memory.setMessageType(TYPE_AI);
                memory.setContent(aiMsg.text() != null ? aiMsg.text() : "");
                if (aiMsg.hasToolExecutionRequests()) {
                    memory.setToolCallsJson(serializeToolRequests(aiMsg.toolExecutionRequests()));
                }
            } else if (message instanceof SystemMessage sysMsg) {
                memory.setMessageType(TYPE_SYSTEM);
                memory.setContent(sysMsg.text());
            } else if (message instanceof ToolExecutionResultMessage toolMsg) {
                memory.setMessageType(TYPE_TOOL_EXECUTION);
                memory.setContent("");
                memory.setToolCallId(toolMsg.id());
                memory.setToolName(toolMsg.toolName());
                memory.setToolResult(toolMsg.text());
            } else {
                log.warn("Unsupported message type: {}", message.getClass().getSimpleName());
                return null;
            }

            return memory;
        } catch (Exception e) {
            log.warn("Failed to convert ChatMessage to UserMemory: {}", e.getMessage());
            return null;
        }
    }

    private String serializeToolRequests(List<ToolExecutionRequest> requests) throws Exception {
        List<Map<String, String>> list = new ArrayList<>();
        for (ToolExecutionRequest req : requests) {
            Map<String, String> map = new HashMap<>();
            map.put("id", req.id());
            map.put("name", req.name());
            map.put("arguments", req.arguments());
            list.add(map);
        }
        return objectMapper.writeValueAsString(list);
    }

    private List<ToolExecutionRequest> deserializeToolRequests(String json) throws Exception {
        List<Map<String, String>> list = objectMapper.readValue(
                json, new TypeReference<List<Map<String, String>>>() {});
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (Map<String, String> map : list) {
            requests.add(ToolExecutionRequest.builder()
                    .id(map.get("id"))
                    .name(map.get("name"))
                    .arguments(map.get("arguments"))
                    .build());
        }
        return requests;
    }
}
