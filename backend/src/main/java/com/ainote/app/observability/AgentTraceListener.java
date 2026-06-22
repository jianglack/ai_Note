package com.ainote.app.observability;

import com.ainote.app.entity.AgentTrace;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Agent 追踪监听器
 * 实现 LangChain4j ChatModelListener 接口，记录每次 LLM 调用的详细信息
 */
@Component
public class AgentTraceListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceListener.class);
    private static final String TRACE_ID_KEY = "traceId";
    private static final String START_TIME_KEY = "startTime";
    private static final String INPUT_TEXT_KEY = "inputText";

    /** 线程级用户 ID 上下文，解决线程池中 SecurityContext 丢失的问题 */
    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    /**
     * 进度回调注册表，keyed by userId
     * 避免 ThreadLocal + ForkJoinPool 不安全的问题（ForkJoinWorkerThread 不继承父线程 ThreadLocal）
     * BiConsumer<step, detail>
     */
    private static final ConcurrentHashMap<String, BiConsumer<String, String>> PROGRESS_CALLBACKS = new ConcurrentHashMap<>();

    /** 工具名 → 中文描述映射 */
    private static final Map<String, String> TOOL_NAME_MAP = Map.ofEntries(
            Map.entry("noteAction", "笔记操作"),
            Map.entry("folderAction", "文件夹操作"),
            Map.entry("scheduleAction", "日程操作")
    );

    /** (工具名, action) → 具体操作描述 */
    private static final Map<String, String> ACTION_DISPLAY_MAP = Map.ofEntries(
            // noteAction
            Map.entry("noteAction:create", "创建笔记"),
            Map.entry("noteAction:search", "搜索笔记"),
            Map.entry("noteAction:listAll", "列出笔记"),
            Map.entry("noteAction:update", "更新笔记"),
            Map.entry("noteAction:delete", "删除笔记"),
            Map.entry("noteAction:confirmDelete", "确认删除笔记"),
            Map.entry("noteAction:permanentDelete", "永久删除笔记"),
            Map.entry("noteAction:confirmPermanentDelete", "确认永久删除"),
            Map.entry("noteAction:addTag", "添加标签"),
            Map.entry("noteAction:removeTag", "移除标签"),
            Map.entry("noteAction:restore", "恢复笔记"),
            Map.entry("noteAction:move", "移动笔记"),
            Map.entry("noteAction:copy", "复制笔记"),
            Map.entry("noteAction:merge", "合并笔记"),
            Map.entry("noteAction:emptyTrash", "清空回收站"),
            Map.entry("noteAction:confirmEmptyTrash", "确认清空回收站"),
            Map.entry("noteAction:listTrash", "查看回收站"),
            Map.entry("noteAction:classify", "智能分类"),
            Map.entry("noteAction:suggestTags", "推荐标签"),
            // folderAction
            Map.entry("folderAction:create", "创建文件夹"),
            Map.entry("folderAction:list", "查看文件夹"),
            Map.entry("folderAction:rename", "重命名文件夹"),
            Map.entry("folderAction:delete", "删除文件夹"),
            Map.entry("folderAction:confirmDelete", "确认删除文件夹"),
            // scheduleAction
            Map.entry("scheduleAction:create", "创建日程"),
            Map.entry("scheduleAction:createBatch", "批量创建日程"),
            Map.entry("scheduleAction:list", "查询日程"),
            Map.entry("scheduleAction:delete", "删除日程"),
            Map.entry("scheduleAction:confirmDelete", "确认删除日程"),
            Map.entry("scheduleAction:extractFromNote", "从笔记提取日程")
    );

    public static void setCurrentUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static void clearCurrentUserId() {
        CURRENT_USER_ID.remove();
    }

    public static void registerProgressCallback(String userId, BiConsumer<String, String> callback) {
        PROGRESS_CALLBACKS.put(userId, callback);
    }

    public static void unregisterProgressCallback(String userId) {
        PROGRESS_CALLBACKS.remove(userId);
    }

    /** 工具名翻译（带 action 细粒度） */
    private String translateToolAction(String toolName, String arguments) {
        try {
            if (arguments != null && !arguments.isEmpty()) {
                var json = objectMapper.readTree(arguments);
                var actionNode = json.get("action");
                if (actionNode != null) {
                    String action = actionNode.asText();
                    String key = toolName + ":" + action;
                    String display = ACTION_DISPLAY_MAP.get(key);
                    if (display != null) return display;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse tool arguments for display: {}", e.getMessage());
        }
        return TOOL_NAME_MAP.getOrDefault(toolName, toolName);
    }

    /** 工具名翻译（仅工具名） */
    private static String translateToolName(String toolName) {
        return TOOL_NAME_MAP.getOrDefault(toolName, toolName);
    }

    private final AgentTraceRepository traceRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String activeModelName;
    private final com.ainote.app.service.CostTrackingService costTrackingService;
    private final com.ainote.app.agent.budget.TokenBudget tokenBudget;

    public AgentTraceListener(
            AgentTraceRepository traceRepository,
            SecurityUtils securityUtils,
            com.ainote.app.service.CostTrackingService costTrackingService,
            com.ainote.app.agent.budget.TokenBudget tokenBudget,
            @Value("${app.observability.enabled:true}") boolean enabled,
            @Value("${app.deepseek.model:deepseek-chat}") String deepseekModel
    ) {
        this.traceRepository = traceRepository;
        this.securityUtils = securityUtils;
        this.costTrackingService = costTrackingService;
        this.tokenBudget = tokenBudget;
        this.objectMapper = new ObjectMapper();
        this.enabled = enabled;
        this.activeModelName = deepseekModel;
        log.info("AgentTraceListener initialized, enabled: {}, model: {}", enabled, activeModelName);
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        if (!enabled) return;

        try {
            String traceId = UUID.randomUUID().toString();
            long startTime = System.currentTimeMillis();

            // 提取输入文本
            String inputText = extractInputText(context);

            // 存储到 attributes 供 onResponse 使用
            context.attributes().put(TRACE_ID_KEY, traceId);
            context.attributes().put(START_TIME_KEY, startTime);
            context.attributes().put(INPUT_TEXT_KEY, inputText);

            log.debug("Agent request started, traceId: {}, input: {}",
                    traceId, truncate(inputText, 100));

        } catch (Exception e) {
            log.warn("Failed to record agent request", e);
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        if (!enabled) return;

        try {
            String traceId = (String) context.attributes().get(TRACE_ID_KEY);
            Long startTime = (Long) context.attributes().get(START_TIME_KEY);
            String inputText = (String) context.attributes().get(INPUT_TEXT_KEY);

            if (traceId == null) {
                log.warn("No traceId found in context attributes");
                return;
            }

            long latencyMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

            // 提取输出文本
            String outputText = extractOutputText(context);

            // 提取 token 使用信息
            TokenUsage tokenUsage = context.chatResponse().tokenUsage();
            int inputTokens = tokenUsage != null && tokenUsage.inputTokenCount() != null
                    ? tokenUsage.inputTokenCount() : 0;
            int outputTokens = tokenUsage != null && tokenUsage.outputTokenCount() != null
                    ? tokenUsage.outputTokenCount() : 0;
            int totalTokens = tokenUsage != null && tokenUsage.totalTokenCount() != null
                    ? tokenUsage.totalTokenCount() : inputTokens + outputTokens;

            // 累计 token 预算
            tokenBudget.addTokens(totalTokens);

            // 提取工具调用信息 & 推送工具调用开始进度
            String toolsCalled = extractToolCalls(context);
            pushToolCallProgress(context);

            // 提取模型名称
            String model = extractModelName(context);

            // 获取当前用户ID
            String userId = getCurrentUserId();

            // 创建追踪记录
            AgentTrace trace = new AgentTrace();
            trace.setId(traceId);
            trace.setUserId(userId);
            trace.setTraceId(traceId);
            trace.setInputText(inputText);
            trace.setOutputText(outputText);
            trace.setToolsCalled(toolsCalled);
            trace.setInputTokens(inputTokens);
            trace.setOutputTokens(outputTokens);
            trace.setTotalTokens(totalTokens);
            trace.setLatencyMs((int) latencyMs);
            trace.setModel(model);
            trace.setCallType("AGENT");
            trace.setStatus("SUCCESS");
            trace.setCreatedAt(LocalDateTime.now());

            // 计算成本
            double cost = costTrackingService.calculateCost(model, inputTokens, outputTokens);
            trace.setEstimatedCostYuan(cost);

            traceRepository.save(trace);

            log.info("Agent trace saved: traceId={}, latency={}ms, tokens={}, model={}, cost=¥{}",
                    traceId, latencyMs, totalTokens, model, String.format("%.6f", cost));

        } catch (Exception e) {
            log.error("Failed to save agent trace", e);
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        if (!enabled) return;

        try {
            String traceId = (String) context.attributes().get(TRACE_ID_KEY);
            Long startTime = (Long) context.attributes().get(START_TIME_KEY);
            String inputText = (String) context.attributes().get(INPUT_TEXT_KEY);

            if (traceId == null) {
                traceId = UUID.randomUUID().toString();
            }

            long latencyMs = startTime != null ? System.currentTimeMillis() - startTime : 0;
            String errorMessage = context.error() != null ? context.error().getMessage() : "Unknown error";
            String userId = getCurrentUserId();

            // 创建错误追踪记录
            AgentTrace trace = new AgentTrace();
            trace.setId(traceId);
            trace.setUserId(userId);
            trace.setTraceId(traceId);
            trace.setInputText(inputText);
            trace.setLatencyMs((int) latencyMs);
            trace.setStatus("ERROR");
            trace.setErrorMessage(errorMessage);
            trace.setCreatedAt(LocalDateTime.now());

            traceRepository.save(trace);

            log.warn("Agent error trace saved: traceId={}, error={}", traceId, errorMessage);

        } catch (Exception e) {
            log.error("Failed to save agent error trace", e);
        }
    }

    /**
     * 提取输入文本
     */
    private String extractInputText(ChatModelRequestContext context) {
        try {
            var messages = context.chatRequest().messages();
            if (messages == null || messages.isEmpty()) {
                return "";
            }
            // 获取最后一条用户消息
            for (int i = messages.size() - 1; i >= 0; i--) {
                var msg = messages.get(i);
                if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                    return ((dev.langchain4j.data.message.UserMessage) msg).singleText();
                }
            }
            return messages.get(messages.size() - 1).toString();
        } catch (Exception e) {
            log.debug("Failed to extract input text: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 提取输出文本
     */
    private String extractOutputText(ChatModelResponseContext context) {
        try {
            var aiMessage = context.chatResponse().aiMessage();
            if (aiMessage != null && aiMessage.text() != null) {
                return aiMessage.text();
            }
            return "";
        } catch (Exception e) {
            log.debug("Failed to extract output text: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 提取工具调用信息
     */
    private String extractToolCalls(ChatModelResponseContext context) {
        try {
            var aiMessage = context.chatResponse().aiMessage();
            if (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
                var toolRequests = aiMessage.toolExecutionRequests();
                List<Map<String, String>> tools = new ArrayList<>();
                for (var req : toolRequests) {
                    Map<String, String> tool = new HashMap<>();
                    tool.put("name", req.name());
                    tool.put("arguments", req.arguments());
                    tools.add(tool);
                }
                return objectMapper.writeValueAsString(tools);
            }
            return null;
        } catch (JsonProcessingException e) {
            log.debug("Failed to serialize tool calls: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取模型名称
     * 从启动时注入的配置中获取实际使用的模型名
     */
    private String extractModelName(ChatModelResponseContext context) {
        return activeModelName;
    }

    /**
     * 获取当前用户ID
     * 优先从 ThreadLocal 读取（线程池场景），fallback 到 SecurityContext
     */
    private String getCurrentUserId() {
        String threadLocalUserId = CURRENT_USER_ID.get();
        if (threadLocalUserId != null) {
            return threadLocalUserId;
        }
        try {
            return securityUtils.getCurrentUserId();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 推送工具调用进度事件
     * 在 onResponse 中检测到 LLM 返回工具调用请求时触发
     * 同一轮中的相同操作合并显示（如"正在创建笔记（5 项）..."）
     */
    private void pushToolCallProgress(ChatModelResponseContext context) {
        try {
            var aiMessage = context.chatResponse().aiMessage();
            if (aiMessage == null || !aiMessage.hasToolExecutionRequests()) return;

            String userId = getCurrentUserId();
            BiConsumer<String, String> callback = PROGRESS_CALLBACKS.get(userId);
            if (callback == null) return;

            // 统计同类操作数量，保持插入顺序
            LinkedHashMap<String, Integer> actionCounts = new LinkedHashMap<>();
            for (var req : aiMessage.toolExecutionRequests()) {
                String displayName = translateToolAction(req.name(), req.arguments());
                actionCounts.merge(displayName, 1, Integer::sum);
            }

            // 每种操作推送一条进度
            for (var entry : actionCounts.entrySet()) {
                String displayName = entry.getKey();
                int count = entry.getValue();
                String detail = count > 1
                        ? "正在" + displayName + "（" + count + " 项）..."
                        : "正在" + displayName + "...";
                callback.accept("tool_call", detail);
                log.debug("Progress pushed: {} (x{}) for user {}", displayName, count, userId);
            }
        } catch (Exception e) {
            log.debug("Failed to push tool call progress: {}", e.getMessage());
        }
    }

}
