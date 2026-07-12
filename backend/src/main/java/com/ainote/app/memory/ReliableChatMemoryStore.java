package com.ainote.app.memory;

import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.ChatMemoryCompactionJob;
import com.ainote.app.entity.ChatMemoryHead;
import com.ainote.app.entity.UserMemory;
import com.ainote.app.repository.ChatMemoryCompactionJobRepository;
import com.ainote.app.repository.ChatMemoryHeadRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
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
    private final ChatMemoryHeadRepository headRepository;
    private final ChatMemoryCompactionJobRepository compactionJobRepository;
    private final MemoryProperties memoryProperties;
    private final ObjectMapper objectMapper;
    private final ShortTermMemoryMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public ReliableChatMemoryStore(
            UserMemoryRepository memoryRepository,
            ChatMemoryHeadRepository headRepository,
            ChatMemoryCompactionJobRepository compactionJobRepository,
            MemoryProperties memoryProperties,
            ObjectMapper objectMapper,
            ShortTermMemoryMetrics metrics,
            PlatformTransactionManager transactionManager) {
        this.memoryRepository = memoryRepository;
        this.headRepository = headRepository;
        this.compactionJobRepository = compactionJobRepository;
        this.memoryProperties = memoryProperties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        log.info("ReliableChatMemoryStore initialized (writeMode={}, modelWindowMaxMessages={})",
                memoryProperties.getChatHistory().getWriteMode(),
                memoryProperties.getChatHistory().getModelWindowMaxMessages());
    }

    ReliableChatMemoryStore(
            UserMemoryRepository memoryRepository,
            ChatMemoryHeadRepository headRepository,
            ChatMemoryCompactionJobRepository compactionJobRepository,
            MemoryProperties memoryProperties,
            ObjectMapper objectMapper,
            ShortTermMemoryMetrics metrics) {
        this.memoryRepository = memoryRepository;
        this.headRepository = headRepository;
        this.compactionJobRepository = compactionJobRepository;
        this.memoryProperties = memoryProperties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.transactionTemplate = null;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String userId = memoryId.toString();
        log.debug("Loading messages for user: {}", userId);
        long startedAt = System.nanoTime();

        try {
            List<ChatMessage> deferredMessages = DeferredMemoryState.peek(userId);
            if (deferredMessages != null) {
                if (!IN_AGENT_LOOP.get()) {
                    List<ChatMessage> sanitized = sanitizeMessages(deferredMessages);
                    metrics.recordLoad(sanitized.size(), System.nanoTime() - startedAt);
                    return sanitized;
                }
                log.debug("Loaded {} deferred messages (in agent loop) for user: {}",
                        deferredMessages.size(), userId);
                metrics.recordLoad(deferredMessages.size(), System.nanoTime() - startedAt);
                return new ArrayList<>(deferredMessages);
            }

            List<UserMemory> memories = new ArrayList<>(memoryRepository
                    .findModelWindowByUserIdOrderBySequenceDesc(
                            userId,
                            PageRequest.of(0, memoryProperties.getChatHistory().getModelWindowMaxMessages())));
            Collections.reverse(memories);
            List<ChatMessage> messages = memories.stream()
                    .map(this::toChatMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<ChatMessage> sanitized = sanitizeMessages(messages);
            if (sanitized.size() != messages.size()) {
                log.warn("Sanitized {} orphaned persisted tool messages for user: {}",
                        messages.size() - sanitized.size(), userId);
            }
            metrics.recordLoad(sanitized.size(), System.nanoTime() - startedAt);
            log.debug("Loaded {} bounded model messages for user: {}", sanitized.size(), userId);
            return sanitized;
        } catch (Exception e) {
            log.error("Failed to load messages for user: {}", userId, e);
            metrics.recordLoad(0, System.nanoTime() - startedAt);
            metrics.recordLoadFailure();
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String userId = memoryId.toString();
        log.debug("Updating messages for user: {}, count: {}", userId, messages.size());

        if (DeferredMemoryState.isActive()) {
            DeferredMemoryState.buffer(userId, messages);
            return;
        }

        try {
            persistWithRetry(userId, messages);
        } catch (Exception e) {
            log.error("Failed to update messages for user: {}", userId, e);
            throw new RuntimeException("Failed to update chat memory", e);
        }
    }

    private void doPersistMessages(String userId, List<ChatMessage> messages) {
        List<ChatMessage> newMessages = messages == null ? List.of() : messages;
        ChatMemoryHead head = lockHead(userId);
        List<UserMemory> oldMemories = loadActiveTail(
                userId,
                memoryProperties.getChatHistory().getModelWindowMaxMessages() + 1);

        if (memoryProperties.getChatHistory().getWriteMode()
                == MemoryProperties.ChatHistoryWriteMode.LEGACY_REWRITE) {
            doRewriteMessages(userId, newMessages, head);
            return;
        }

        doAppendMessages(userId, oldMemories, newMessages, head);
    }

    private ChatMemoryHead lockHead(String userId) {
        headRepository.ensureExists(userId);
        return headRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("chat memory head missing after ensure"));
    }

    private List<UserMemory> loadActiveTail(String userId, int limit) {
        List<UserMemory> rows = new ArrayList<>(memoryRepository
                .findModelWindowByUserIdOrderBySequenceDesc(userId, PageRequest.of(0, limit)));
        Collections.reverse(rows);
        return rows;
    }

    private void doRewriteMessages(String userId, List<ChatMessage> messages, ChatMemoryHead head) {
        memoryRepository.deleteByUserId(userId);
        compactionJobRepository.deleteByUserId(userId);

        List<UserMemory> batch = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            UserMemory memory = toUserMemory(userId, messages.get(i));
            if (memory != null) {
                memory.setSequenceNumber(i);
                batch.add(memory);
            }
        }

        memoryRepository.saveAll(batch);
        head.setNextSequenceNumber(batch.size());
        head.setLastCompactionEnqueuedSequence(-1);
        headRepository.save(head);
        metrics.recordAppend(batch.size());
        log.debug("Batch saved {} messages for user: {} (legacy full rewrite)", batch.size(), userId);
    }

    private void doAppendMessages(
            String userId,
            List<UserMemory> oldMemories,
            List<ChatMessage> newMessages,
            ChatMemoryHead head) {
        List<ChatMessage> existingMessages = oldMemories.stream()
                .map(this::toChatMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        int overlap = findSuffixPrefixOverlap(existingMessages, newMessages);

        int trimCount = Math.max(0, existingMessages.size() - overlap);
        if (trimCount > 0) {
            List<UserMemory> trimmed = oldMemories.subList(0, trimCount);
            LocalDateTime now = LocalDateTime.now();
            trimmed.forEach(row -> row.setTrimmedAt(now));
            memoryRepository.saveAll(trimmed);
            metrics.recordTrim(trimmed.size());
            maybeEnqueueCompaction(userId, head, trimmed.get(trimmed.size() - 1).getSequenceNumber());
        }

        if (overlap >= newMessages.size()) {
            headRepository.save(head);
            log.debug("No new chat messages to append for user: {} (overlap={}, trimmed={})",
                    userId, overlap, trimCount);
            return;
        }
        List<ChatMessage> appendMessages = newMessages.subList(overlap, newMessages.size());
        int observedNext = oldMemories.stream()
                .map(UserMemory::getSequenceNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(0);
        int nextSequenceNumber = Math.max(head.getNextSequenceNumber(), observedNext);
        List<UserMemory> batch = new ArrayList<>(appendMessages.size());
        for (int i = 0; i < appendMessages.size(); i++) {
            UserMemory memory = toUserMemory(userId, appendMessages.get(i));
            if (memory != null) {
                memory.setSequenceNumber(nextSequenceNumber + i);
                batch.add(memory);
            }
        }

        if (batch.isEmpty()) {
            headRepository.save(head);
            log.debug("No persistable chat messages to append for user: {}", userId);
            return;
        }

        memoryRepository.saveAll(batch);
        head.setNextSequenceNumber(nextSequenceNumber + batch.size());
        headRepository.save(head);
        metrics.recordAppend(batch.size());
        log.debug("Appended {} messages for user: {} (overlap={}, existing={})",
                batch.size(), userId, overlap, existingMessages.size());
    }

    private void maybeEnqueueCompaction(String userId, ChatMemoryHead head, int highestTrimmedSequence) {
        if (!memoryProperties.getChatHistory().isCompactionEnabled()) return;

        int fromSequence = head.getLastCompactionEnqueuedSequence() + 1;
        if (highestTrimmedSequence < fromSequence) return;

        int maxMessages = memoryProperties.getChatHistory().getCompactionMaxSourceMessages();
        int tentativeTo = Math.min(highestTrimmedSequence, fromSequence + maxMessages - 1);
        List<UserMemory> candidates = memoryRepository.findByUserIdAndSequenceRange(
                userId, fromSequence, tentativeTo);
        if (candidates.isEmpty()) return;

        int maxCharacters = memoryProperties.getChatHistory().getCompactionMaxSourceCharacters();
        int consumedCharacters = 0;
        int selectedCount = 0;
        for (UserMemory row : candidates) {
            int estimatedCharacters = 16 + (row.getContent() == null ? 0 : row.getContent().length());
            if (selectedCount > 0 && consumedCharacters + estimatedCharacters > maxCharacters) break;
            consumedCharacters += Math.min(maxCharacters, estimatedCharacters);
            selectedCount++;
        }
        List<UserMemory> source = candidates.subList(0, selectedCount);
        int toSequence = source.get(source.size() - 1).getSequenceNumber();
        int userTurns = (int) source.stream()
                .filter(row -> TYPE_USER.equals(row.getMessageType()))
                .count();
        boolean fullBatch = source.size() >= maxMessages || toSequence < tentativeTo;
        if (userTurns < memoryProperties.getChatHistory().getCompactionMinUserTurns() && !fullBatch) return;

        if (!compactionJobRepository.existsByUserIdAndFromSequenceAndToSequence(
                userId, fromSequence, toSequence)) {
            ChatMemoryCompactionJob job = new ChatMemoryCompactionJob();
            job.setUserId(userId);
            job.setFromSequence(fromSequence);
            job.setToSequence(toSequence);
            job.setUserMessageCount(userTurns);
            compactionJobRepository.save(job);
        }
        head.setLastCompactionEnqueuedSequence(toSequence);
    }

    private int findSuffixPrefixOverlap(List<ChatMessage> existingMessages, List<ChatMessage> incomingMessages) {
        int max = Math.min(existingMessages.size(), incomingMessages.size());
        for (int length = max; length > 0; length--) {
            boolean matches = true;
            int existingStart = existingMessages.size() - length;
            for (int i = 0; i < length; i++) {
                if (!sameMessage(existingMessages.get(existingStart + i), incomingMessages.get(i))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return length;
            }
        }
        return 0;
    }

    private boolean sameMessage(ChatMessage left, ChatMessage right) {
        return messageSignature(left).equals(messageSignature(right));
    }

    private String messageSignature(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return TYPE_USER + "\u001f" + nullSafe(userMsg.singleText());
        }
        if (message instanceof AiMessage aiMsg) {
            String toolRequests = "";
            if (aiMsg.hasToolExecutionRequests()) {
                toolRequests = aiMsg.toolExecutionRequests().stream()
                        .map(req -> nullSafe(req.id()) + "\u001d"
                                + nullSafe(req.name()) + "\u001d"
                                + nullSafe(req.arguments()))
                        .collect(Collectors.joining("\u001e"));
            }
            return TYPE_AI + "\u001f" + nullSafe(aiMsg.text()) + "\u001f" + toolRequests;
        }
        if (message instanceof SystemMessage sysMsg) {
            return TYPE_SYSTEM + "\u001f" + nullSafe(sysMsg.text());
        }
        if (message instanceof ToolExecutionResultMessage toolMsg) {
            return TYPE_TOOL_EXECUTION + "\u001f"
                    + nullSafe(toolMsg.id()) + "\u001f"
                    + nullSafe(toolMsg.toolName()) + "\u001f"
                    + nullSafe(toolMsg.text());
        }
        return message.getClass().getName() + "\u001f" + message;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public void flushDeferredWrites() {
        Map<String, List<ChatMessage>> pending = DeferredMemoryState.drain();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Flushing {} deferred memory writes", pending.size());
        for (var entry : pending.entrySet()) {
            persistWithRetry(entry.getKey(), entry.getValue());
        }
    }

    private void persistWithRetry(String userId, List<ChatMessage> messages) {
        int maxAttempts = memoryProperties.getChatHistory().getFlushMaxAttempts();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                executePersistTransaction(userId, messages);
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt >= maxAttempts) break;
                metrics.recordFlushRetry();
                sleepBeforeRetry(attempt);
            }
        }
        metrics.recordFlushFailure();
        throw lastFailure == null ? new IllegalStateException("chat memory flush failed") : lastFailure;
    }

    private void executePersistTransaction(String userId, List<ChatMessage> messages) {
        if (transactionTemplate == null) {
            doPersistMessages(userId, messages);
            return;
        }
        transactionTemplate.executeWithoutResult(status -> doPersistMessages(userId, messages));
    }

    private void sleepBeforeRetry(int attempt) {
        long baseDelay = memoryProperties.getChatHistory().getFlushRetryDelayMs();
        if (baseDelay <= 0) return;
        try {
            Thread.sleep(Math.min(1000, baseDelay * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("chat memory flush retry interrupted", e);
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

        Runnable delete = () -> {
            compactionJobRepository.deleteByUserId(userId);
            memoryRepository.deleteByUserId(userId);
            headRepository.deleteById(userId);
        };
        if (transactionTemplate == null) {
            delete.run();
        } else {
            transactionTemplate.executeWithoutResult(status -> delete.run());
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
