package com.ainote.ai.controller;

import com.ainote.ai.agent.CancellationToken;
import com.ainote.ai.entity.AgentTrace;
import com.ainote.ai.model.AiChatRequest;
import com.ainote.ai.model.AiChatResponse;
import com.ainote.ai.model.ChatHistoryItem;
import com.ainote.ai.repository.AgentTraceRepository;
import com.ainote.ai.repository.UserMemoryRepository;
import com.ainote.ai.service.AgentService;
import com.ainote.common.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AgentService agentService;
    private final AgentTraceRepository traceRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public AiController(AgentService agentService,
                        AgentTraceRepository traceRepository,
                        UserMemoryRepository userMemoryRepository,
                        ObjectMapper objectMapper,
                        @Qualifier("securityExecutor") ExecutorService executorService) {
        this.agentService = agentService;
        this.traceRepository = traceRepository;
        this.userMemoryRepository = userMemoryRepository;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
    }

    private String getCurrentUserId() {
        Long userId = UserContext.getCurrentUserId();
        return userId != null ? userId.toString() : "anonymous";
    }

    @GetMapping("/chat/history")
    public ResponseEntity<List<ChatHistoryItem>> getChatHistory() {
        try {
            String userId = getCurrentUserId();
            var memories = userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50));
            List<ChatHistoryItem> history = memories.stream()
                    .filter(m -> "USER".equals(m.getMessageType()) || "AI".equals(m.getMessageType()))
                    .map(m -> new ChatHistoryItem(
                            m.getId().toString(),
                            "USER".equals(m.getMessageType()) ? "user" : "assistant",
                            m.getContent(),
                            m.getCreatedAt()
                    ))
                    .collect(Collectors.toList());
            Collections.reverse(history);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/chat/history")
    public ResponseEntity<Void> clearChatMemory() {
        try {
            String userId = getCurrentUserId();
            agentService.clearMemory(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/chat/save")
    public ResponseEntity<Void> saveChatMessages(@RequestBody Map<String, String> body) {
        try {
            String userId = getCurrentUserId();
            String userMsg = body.get("userMessage");
            String aiReply = body.get("aiReply");
            if (userMsg != null) {
                var mem = new com.ainote.ai.entity.UserMemory();
                mem.setUserId(userId);
                mem.setMessageType("USER");
                mem.setContent(userMsg);
                userMemoryRepository.save(mem);
            }
            if (aiReply != null) {
                var mem = new com.ainote.ai.entity.UserMemory();
                mem.setUserId(userId);
                mem.setMessageType("AI");
                mem.setContent(aiReply);
                userMemoryRepository.save(mem);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        try {
            return ResponseEntity.ok(agentService.chat(request.getQuery(), request.getNoteIds()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        String userId = getCurrentUserId();

        // 创建取消令牌，SSE 断开时取消 Agent 执行
        CancellationToken cancelToken = agentService.createCancelToken(userId);

        emitter.onCompletion(() -> cancelToken.cancel());
        emitter.onTimeout(() -> cancelToken.cancel());
        emitter.onError(e -> cancelToken.cancel());

        executorService.execute(() -> {
            try {
                agentService.chatStream(request.getQuery(), request.getNoteIds(), userId,
                        new AgentService.StreamCallback() {
                            @Override
                            public void onToken(String token) {
                                try {
                                    emitter.send(SseEmitter.event().name("token").data(token));
                                } catch (IOException e) {
                                    emitter.completeWithError(e);
                                }
                            }

                            @Override
                            public void onComplete(AiChatResponse response) {
                                try {
                                    emitter.send(SseEmitter.event().name("complete").data(response));
                                    emitter.complete();
                                } catch (IOException e) {
                                    emitter.completeWithError(e);
                                }
                            }

                            @Override
                            public void onError(String error) {
                                try {
                                    emitter.send(SseEmitter.event().name("error").data(error));
                                    emitter.complete();
                                } catch (IOException e) {
                                    emitter.completeWithError(e);
                                }
                            }

                            @Override
                            public void onProgress(String step, String detail) {
                                try {
                                    emitter.send(SseEmitter.event().name("progress")
                                            .data(Map.of("step", step, "detail", detail),
                                                    MediaType.APPLICATION_JSON));
                                } catch (IOException e) {
                                    // client may have disconnected
                                }
                            }
                        });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/traces")
    public ResponseEntity<List<AgentTrace>> getTraces(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            String userId = getCurrentUserId();
            List<AgentTrace> traces = traceRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, PageRequest.of(0, limit));
            return ResponseEntity.ok(traces);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/traces/stats")
    public ResponseEntity<Map<String, Object>> getTraceStats() {
        try {
            String userId = getCurrentUserId();
            long totalCalls = traceRepository.countByUserId(userId);
            long totalTokens = traceRepository.sumTotalTokensByUserId(userId);

            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = todayStart.plusDays(1);
            long todayCalls = traceRepository.countByUserIdAndCreatedAtBetween(userId, todayStart, todayEnd);
            long todayTokens = traceRepository.sumTotalTokensByUserIdAndDateRange(userId, todayStart, todayEnd);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalCalls", totalCalls);
            stats.put("totalTokens", totalTokens);
            stats.put("todayCalls", todayCalls);
            stats.put("todayTokens", todayTokens);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
