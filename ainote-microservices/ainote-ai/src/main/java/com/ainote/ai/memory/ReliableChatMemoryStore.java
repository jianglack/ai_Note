package com.ainote.ai.memory;

import com.ainote.ai.entity.UserMemory;
import com.ainote.ai.repository.UserMemoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 可靠的 ChatMemoryStore 实现
 * 策略：每次 updateMessages 全量删除 + 全量写入
 */
@Component
public class ReliableChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(ReliableChatMemoryStore.class);

    private static final String TYPE_USER = "USER";
    private static final String TYPE_AI = "AI";
    private static final String TYPE_SYSTEM = "SYSTEM";
    private static final String TYPE_TOOL_EXECUTION = "TOOL_EXECUTION_RESULT";

    private final UserMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    public ReliableChatMemoryStore(UserMemoryRepository memoryRepository, ObjectMapper objectMapper) {
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
        log.info("ReliableChatMemoryStore initialized (full-delete + full-rewrite strategy)");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String userId = memoryId.toString();
        log.debug("Loading messages for user: {}", userId);

        try {
            List<UserMemory> memories = memoryRepository.findAllByUserIdOrderByCreatedAtAsc(userId);

            List<ChatMessage> messages = memories.stream()
                    .map(this::toChatMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<ChatMessage> sanitized = sanitizeMessages(messages);
            if (sanitized.size() != messages.size()) {
                log.warn("Sanitized {} orphaned tool messages for user: {}",
                        messages.size() - sanitized.size(), userId);
            }
            return sanitized;

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

        try {
            memoryRepository.deleteByUserId(userId);

            for (ChatMessage message : messages) {
                UserMemory memory = toUserMemory(userId, message);
                if (memory != null) {
                    memoryRepository.save(memory);
                }
            }

            log.debug("Saved {} messages for user: {} (full rewrite)", messages.size(), userId);

        } catch (Exception e) {
            log.error("Failed to update messages for user: {}", userId, e);
            throw new RuntimeException("Failed to update chat memory", e);
        }
    }

    private List<ChatMessage> sanitizeMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return new ArrayList<>();

        Set<String> declaredToolCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    if (req.id() != null) declaredToolCallIds.add(req.id());
                }
            }
        }

        Set<String> respondedToolCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                if (toolMsg.id() != null) respondedToolCallIds.add(toolMsg.id());
            }
        }

        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                if (toolMsg.id() != null && declaredToolCallIds.contains(toolMsg.id())) {
                    result.add(msg);
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
            return switch (memory.getMessageType()) {
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
                    log.warn("Unknown message type: {}", memory.getMessageType());
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
