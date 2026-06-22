package com.ainote.app.service;

import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 运行指标服务
 *
 * 双数据源策略：
 * 1. Micrometer MeterRegistry — 实时计数器/定时器，暴露至 /actuator/prometheus
 * 2. DB 聚合查询 — 从 agent_traces / task_plans / task_steps 表获取持久化指标
 *
 * /api/admin/agent-metrics 返回的 JSON 同时融合两者数据，
 * 前端 AgentMetricsDashboard 直接消费。
 */
@Service
public class AgentMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsService.class);

    private final MeterRegistry meterRegistry;
    private final AgentTraceRepository traceRepository;
    private final TaskPlanRepository planRepository;
    private final TaskStepRepository stepRepository;

    // ── Micrometer counters (real-time, since process start) ──
    private final Counter planCreatedCounter;
    private final Counter planCompletedCounter;
    private final Counter planFailedCounter;
    private final Counter planRolledBackCounter;
    private final Counter stepExecutedCounter;
    private final Counter stepSucceededCounter;
    private final Counter stepFailedCounter;
    private final Counter stepRetriedCounter;
    private final Counter compensationExecutedCounter;
    private final Counter compensationVerifiedCounter;
    private final Counter compensationFailedCounter;

    // ── In-memory tool-level detail (Micrometer tags per tool) ──
    private final ConcurrentHashMap<String, ToolDetail> toolDetails = new ConcurrentHashMap<>();

    public AgentMetricsService(MeterRegistry meterRegistry,
                               AgentTraceRepository traceRepository,
                               TaskPlanRepository planRepository,
                               TaskStepRepository stepRepository) {
        this.meterRegistry = meterRegistry;
        this.traceRepository = traceRepository;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;

        // Register Micrometer counters
        this.planCreatedCounter = Counter.builder("agent.plans.created").description("Plans created").register(meterRegistry);
        this.planCompletedCounter = Counter.builder("agent.plans.completed").description("Plans completed").register(meterRegistry);
        this.planFailedCounter = Counter.builder("agent.plans.failed").description("Plans failed").register(meterRegistry);
        this.planRolledBackCounter = Counter.builder("agent.plans.rolled_back").description("Plans rolled back").register(meterRegistry);
        this.stepExecutedCounter = Counter.builder("agent.steps.executed").description("Steps executed").register(meterRegistry);
        this.stepSucceededCounter = Counter.builder("agent.steps.succeeded").description("Steps succeeded").register(meterRegistry);
        this.stepFailedCounter = Counter.builder("agent.steps.failed").description("Steps failed").register(meterRegistry);
        this.stepRetriedCounter = Counter.builder("agent.steps.retried").description("Steps retried").register(meterRegistry);
        this.compensationExecutedCounter = Counter.builder("agent.compensation.executed").register(meterRegistry);
        this.compensationVerifiedCounter = Counter.builder("agent.compensation.verified").register(meterRegistry);
        this.compensationFailedCounter = Counter.builder("agent.compensation.failed").register(meterRegistry);
    }

    // ============ 记录方法（Micrometer + 内存明细） ============

    public void recordToolCall(String toolName, String action, boolean success, long latencyMs) {
        // Micrometer timer per tool
        Timer.builder("agent.tool.latency")
                .tag("tool", toolName)
                .tag("status", success ? "success" : "error")
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);

        Counter.builder("agent.tool.calls")
                .tag("tool", toolName)
                .tag("status", success ? "success" : "error")
                .register(meterRegistry)
                .increment();

        // In-memory detail for last-failure tracking
        ToolDetail detail = toolDetails.computeIfAbsent(toolName, k -> new ToolDetail());
        detail.totalCalls.incrementAndGet();
        if (success) {
            detail.successCalls.incrementAndGet();
        } else {
            detail.failureCalls.incrementAndGet();
            detail.lastFailureTime = Instant.now();
            detail.lastFailureAction = action;
        }
        detail.totalLatencyMs.addAndGet(latencyMs);
    }

    public void recordToolRetry(String toolName) {
        Counter.builder("agent.tool.retries").tag("tool", toolName).register(meterRegistry).increment();
        ToolDetail detail = toolDetails.computeIfAbsent(toolName, k -> new ToolDetail());
        detail.retryCalls.incrementAndGet();
    }

    public void recordPlanCreated() { planCreatedCounter.increment(); }
    public void recordPlanCompleted() { planCompletedCounter.increment(); }
    public void recordPlanFailed() { planFailedCounter.increment(); }
    public void recordPlanRolledBack() { planRolledBackCounter.increment(); }

    public void recordStepExecution(boolean success) {
        stepExecutedCounter.increment();
        if (success) stepSucceededCounter.increment();
        else stepFailedCounter.increment();
    }

    public void recordStepRetry() { stepRetriedCounter.increment(); }

    public void recordCompensation(boolean executed, boolean verified) {
        if (executed) {
            compensationExecutedCounter.increment();
            if (verified) compensationVerifiedCounter.increment();
            else compensationFailedCounter.increment();
        } else {
            compensationFailedCounter.increment();
        }
    }

    // ============ 查询方法（融合 DB + Micrometer） ============

    /**
     * 返回与前端 MetricsData 结构匹配的 JSON。
     * 优先使用 DB 持久化数据；DB 无数据时回退到 Micrometer 实时计数。
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);

        // ── 1) 计划指标（从 DB） ──
        long dbPlansTotal = planRepository.count();
        long dbPlansCompleted = planRepository.countByStatus("COMPLETED");
        long dbPlansFailed = planRepository.countByStatus("FAILED");
        long dbPlansRolledBack = planRepository.countByStatus("CANCELLED");

        // 回退到 Micrometer（如果 DB 没数据但内存有）
        long plansTotal = dbPlansTotal > 0 ? dbPlansTotal : (long) planCreatedCounter.count();
        long plansCompleted = dbPlansTotal > 0 ? dbPlansCompleted : (long) planCompletedCounter.count();
        long plansFailed = dbPlansTotal > 0 ? dbPlansFailed : (long) planFailedCounter.count();
        long plansRolledBack = dbPlansTotal > 0 ? dbPlansRolledBack : (long) planRolledBackCounter.count();

        double planSuccessRate = plansTotal > 0 ? (plansCompleted * 100.0 / plansTotal) : 0;

        result.put("plansTotal", plansTotal);
        result.put("plansCompleted", plansCompleted);
        result.put("plansFailed", plansFailed);
        result.put("plansRolledBack", plansRolledBack);
        result.put("planSuccessRate", round1(planSuccessRate));

        // ── 2) 步骤指标（从 DB） ──
        long dbStepsTotal = stepRepository.count();
        long dbStepsSucceeded = stepRepository.countByStatus("SUCCESS");
        long dbStepsFailed = stepRepository.countByStatus("FAILED");
        // 重试次数只有内存有
        long stepsRetried = (long) stepRetriedCounter.count();

        long stepsTotal = dbStepsTotal > 0 ? dbStepsTotal : (long) stepExecutedCounter.count();
        long stepsSucceeded = dbStepsTotal > 0 ? dbStepsSucceeded : (long) stepSucceededCounter.count();
        long stepsFailed = dbStepsTotal > 0 ? dbStepsFailed : (long) stepFailedCounter.count();
        double stepRetryRate = stepsTotal > 0 ? (stepsRetried * 100.0 / stepsTotal) : 0;

        result.put("stepsTotal", stepsTotal);
        result.put("stepsSucceeded", stepsSucceeded);
        result.put("stepsFailed", stepsFailed);
        result.put("stepsRetried", stepsRetried);
        result.put("stepRetryRate", round1(stepRetryRate));

        // ── 3) 工具指标（DB 聚合 + 内存明细） ──
        List<Map<String, Object>> toolsList = new ArrayList<>();
        long totalToolCalls = 0;

        try {
            List<Object[]> dbTools = traceRepository.aggregateToolMetrics(since24h);
            for (Object[] row : dbTools) {
                String name = (String) row[0];
                long calls = ((Number) row[1]).longValue();
                long failures = ((Number) row[2]).longValue();
                long avgLatency = ((Number) row[3]).longValue();
                Object lastFailObj = row[4]; // can be null

                totalToolCalls += calls;
                double rate = calls > 0 ? (failures * 100.0 / calls) : 0;

                String lastFailure = "从未";
                if (lastFailObj != null) {
                    // Format relative time
                    lastFailure = formatRelativeTime(lastFailObj);
                }

                // Enrich with in-memory detail
                ToolDetail detail = toolDetails.get(name);
                if (detail != null && detail.lastFailureTime != null && "从未".equals(lastFailure)) {
                    lastFailure = formatRelativeInstant(detail.lastFailureTime);
                }

                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", name);
                tool.put("calls", calls);
                tool.put("failures", failures);
                tool.put("rate", round1(rate));
                tool.put("latency", avgLatency);
                tool.put("lastFailure", lastFailure);
                tool.put("desc", getToolDescription(name));
                toolsList.add(tool);
            }
        } catch (Exception e) {
            log.warn("DB 工具聚合查询失败，回退到内存指标: {}", e.getMessage());
        }

        // 如果 DB 没有工具数据，从内存获取
        if (toolsList.isEmpty() && !toolDetails.isEmpty()) {
            for (Map.Entry<String, ToolDetail> entry : toolDetails.entrySet()) {
                ToolDetail d = entry.getValue();
                long calls = d.totalCalls.get();
                long failures = d.failureCalls.get();
                totalToolCalls += calls;

                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", entry.getKey());
                tool.put("calls", calls);
                tool.put("failures", failures);
                tool.put("rate", calls > 0 ? round1(failures * 100.0 / calls) : 0);
                tool.put("latency", calls > 0 ? d.totalLatencyMs.get() / calls : 0);
                tool.put("lastFailure", d.lastFailureTime != null
                        ? formatRelativeInstant(d.lastFailureTime) : "从未");
                tool.put("desc", getToolDescription(entry.getKey()));
                toolsList.add(tool);
            }
        }

        result.put("tools", toolsList);
        result.put("toolsCalls", totalToolCalls);
        result.put("toolsCount", toolsList.size());

        // ── 4) 补偿指标 ──
        long compTotal = (long) compensationExecutedCounter.count();
        long compVerified = (long) compensationVerifiedCounter.count();
        long compFailed = (long) compensationFailedCounter.count();
        double compRate = compTotal > 0 ? (compVerified * 100.0 / compTotal) : 0;

        result.put("compensationTotal", compTotal);
        result.put("compensationVerified", compVerified);
        result.put("compensationFailed", compFailed);
        result.put("compensationRate", round1(compRate));

        // ── 5) 元数据 ──
        result.put("lastSampled", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return result;
    }

    // ============ 辅助方法 ============

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String formatRelativeTime(Object timestamp) {
        try {
            LocalDateTime time;
            if (timestamp instanceof java.sql.Timestamp ts) {
                time = ts.toLocalDateTime();
            } else {
                time = LocalDateTime.parse(timestamp.toString().replace(" ", "T"));
            }
            Duration diff = Duration.between(time, LocalDateTime.now());
            if (diff.toMinutes() < 1) return "刚刚";
            if (diff.toMinutes() < 60) return diff.toMinutes() + " 分钟前";
            if (diff.toHours() < 24) return diff.toHours() + " 小时前";
            return diff.toDays() + " 天前";
        } catch (Exception e) {
            return "未知";
        }
    }

    private String formatRelativeInstant(Instant instant) {
        Duration diff = Duration.between(instant, Instant.now());
        if (diff.toMinutes() < 1) return "刚刚";
        if (diff.toMinutes() < 60) return diff.toMinutes() + " 分钟前";
        if (diff.toHours() < 24) return diff.toHours() + " 小时前";
        return diff.toDays() + " 天前";
    }

    private String getToolDescription(String toolName) {
        return switch (toolName) {
            case "searchNotes", "noteAction" -> "笔记增删改查";
            case "createNote" -> "创建笔记";
            case "updateNote" -> "更新笔记";
            case "deleteNote" -> "删除笔记";
            case "searchSchedules", "scheduleAction" -> "日程与提醒";
            case "createSchedule" -> "创建日程";
            case "aiSummarize" -> "LLM 摘要生成";
            case "moveNoteToFolder" -> "移动笔记";
            case "copyNote" -> "复制笔记";
            case "mergeNotes" -> "合并笔记";
            case "createFolder" -> "创建文件夹";
            case "deleteFolder" -> "删除文件夹";
            default -> toolName;
        };
    }

    // ============ 内部类 ============

    private static class ToolDetail {
        final AtomicLong totalCalls = new AtomicLong();
        final AtomicLong successCalls = new AtomicLong();
        final AtomicLong failureCalls = new AtomicLong();
        final AtomicLong retryCalls = new AtomicLong();
        final AtomicLong totalLatencyMs = new AtomicLong();
        volatile Instant lastFailureTime;
        volatile String lastFailureAction;
    }
}
