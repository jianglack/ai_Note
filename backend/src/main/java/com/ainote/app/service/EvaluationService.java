package com.ainote.app.service;

import com.ainote.app.entity.*;
import com.ainote.app.repository.*;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * RAG/Agent 评估服务 —— 全部委托给 RAGAS Python 微服务（标准 RAGAS 0.4 框架）
 *
 * RAG 指标：Faithfulness, AnswerRelevancy, ContextPrecision, ContextRecall, FactualCorrectness
 * Agent 指标：ToolCallAccuracy, AgentGoalAccuracy, TopicAdherence
 * 效率数据：从 agent_traces 表获取真实 token 消耗和延迟
 *
 * Java 端职责：数据集 CRUD、检索 context、调用 Agent、读取 trace、调用 RAGAS 服务、存储结果
 * Python 端职责：所有评估指标计算（RAGAS 框架）
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    @Value("${app.ragas.url:http://localhost:8090}")
    private String ragasUrl;

    @Value("${app.ragas.enabled:false}")
    private boolean ragasEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    private final EvalDatasetRepository datasetRepository;
    private final EvalItemRepository itemRepository;
    private final EvalRunRepository runRepository;
    private final EvalResultRepository resultRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final SecurityUtils securityUtils;
    private final LangChain4jRagService ragService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public EvaluationService(
            EvalDatasetRepository datasetRepository,
            EvalItemRepository itemRepository,
            EvalRunRepository runRepository,
            EvalResultRepository resultRepository,
            AgentTraceRepository agentTraceRepository,
            SecurityUtils securityUtils,
            LangChain4jRagService ragService,
            AgentService agentService,
            ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.itemRepository = itemRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.securityUtils = securityUtils;
        this.ragService = ragService;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    // ── Dataset CRUD ──

    public EvalDataset createDataset(String name, String description, String type) {
        String userId = securityUtils.getCurrentUserId();
        EvalDataset ds = new EvalDataset();
        ds.setUserId(userId);
        ds.setName(name);
        ds.setDescription(description);
        ds.setDatasetType(type != null ? type : "rag");
        return datasetRepository.save(ds);
    }

    public List<EvalDataset> listDatasets() {
        String userId = securityUtils.getCurrentUserId();
        return datasetRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public EvalDataset getDataset(String id) {
        String userId = securityUtils.getCurrentUserId();
        return getDatasetForUser(id, userId);
    }

    public void deleteDataset(String id) {
        String userId = securityUtils.getCurrentUserId();
        EvalDataset dataset = getDatasetForUser(id, userId);
        datasetRepository.delete(dataset);
    }

    // ── Items ──

    public EvalItem addItem(String datasetId, String question, String expectedAnswer, String expectedToolCalls) {
        String userId = securityUtils.getCurrentUserId();
        getDatasetForUser(datasetId, userId);

        EvalItem item = new EvalItem();
        item.setDatasetId(datasetId);
        item.setQuestion(question);
        item.setExpectedAnswer(expectedAnswer);
        item.setExpectedToolCalls(expectedToolCalls);
        EvalItem saved = itemRepository.save(item);

        EvalDataset ds = datasetRepository.findById(datasetId).orElseThrow();
        ds.setItemCount(itemRepository.countByDatasetId(datasetId));
        datasetRepository.save(ds);

        return saved;
    }

    public List<EvalItem> getItems(String datasetId) {
        String userId = securityUtils.getCurrentUserId();
        getDatasetForUser(datasetId, userId);
        return itemRepository.findByDatasetId(datasetId);
    }

    public void deleteItem(String itemId) {
        EvalItem item = itemRepository.findById(itemId).orElseThrow();
        String userId = securityUtils.getCurrentUserId();
        getDatasetForUser(item.getDatasetId(), userId);
        itemRepository.delete(item);
        EvalDataset ds = getDatasetForUser(item.getDatasetId(), userId);
        ds.setItemCount(itemRepository.countByDatasetId(item.getDatasetId()));
        datasetRepository.save(ds);
    }

    // ══════════════════════════════════════════════════════
    //  Evaluation Run
    // ══════════════════════════════════════════════════════

    @Async("taskExecutor")
    public CompletableFuture<EvalRun> runEvaluation(String datasetId, String runType, String config, String userId) {
        getDatasetForUser(datasetId, userId);

        EvalRun run = new EvalRun();
        run.setDatasetId(datasetId);
        run.setUserId(userId);
        run.setRunType(runType);
        run.setConfig(config);
        run = runRepository.save(run);

        List<EvalItem> items = itemRepository.findByDatasetId(datasetId);
        log.info("Starting evaluation run {} with {} items, type={}", run.getId(), items.size(), runType);

        List<EvalResult> results = new ArrayList<>();
        double totalFaithfulness = 0, totalRelevancy = 0, totalPrecision = 0, totalRecall = 0;
        double totalGoalAcc = 0, totalToolAcc = 0, totalTopicAdh = 0, totalErrRecovery = 0, totalTokens = 0;
        int ragCount = 0, agentCount = 0, errRecoveryCount = 0;

        for (EvalItem item : items) {
            try {
                EvalResult result = evaluateItem(item, runType, run.getId(), userId);
                results.add(result);
                resultRepository.save(result);

                if (result.getFaithfulness() != null) { totalFaithfulness += result.getFaithfulness(); ragCount++; }
                if (result.getAnswerRelevancy() != null) totalRelevancy += result.getAnswerRelevancy();
                if (result.getContextPrecision() != null) totalPrecision += result.getContextPrecision();
                if (result.getContextRecall() != null) totalRecall += result.getContextRecall();
                if (result.getTaskCompletion() != null) { totalGoalAcc += result.getTaskCompletion(); agentCount++; }
                if (result.getToolAccuracy() != null) totalToolAcc += result.getToolAccuracy();
                if (result.getConsistency() != null) totalTopicAdh += result.getConsistency();
                if (result.getErrorRecoveryRate() != null) { totalErrRecovery += result.getErrorRecoveryRate(); errRecoveryCount++; }
                if (result.getTotalTokens() != null) totalTokens += result.getTotalTokens();

            } catch (Exception e) {
                log.error("Evaluation failed for item {}: {}", item.getId(), e.getMessage());
            }
        }

        // Summary
        Map<String, Object> summary = new HashMap<>();
        if (ragCount > 0) {
            summary.put("faithfulness", round(totalFaithfulness / ragCount));
            summary.put("answerRelevancy", round(totalRelevancy / ragCount));
            summary.put("contextPrecision", round(totalPrecision / ragCount));
            summary.put("contextRecall", round(totalRecall / ragCount));
        }
        if (agentCount > 0) {
            summary.put("agentGoalAccuracy", round(totalGoalAcc / agentCount));
            summary.put("toolCallAccuracy", round(totalToolAcc / agentCount));
            summary.put("topicAdherence", round(totalTopicAdh / agentCount));
            if (errRecoveryCount > 0) summary.put("errorRecoveryRate", round(totalErrRecovery / errRecoveryCount));
            summary.put("avgTokens", Math.round(totalTokens / agentCount));
        }
        summary.put("totalItems", items.size());
        summary.put("evaluatedItems", results.size());

        try { run.setSummary(objectMapper.writeValueAsString(summary)); }
        catch (Exception e) { run.setSummary("{}"); }
        run.setStatus("completed");
        run.setCompletedAt(LocalDateTime.now());
        runRepository.save(run);

        log.info("Evaluation run {} completed. Summary: {}", run.getId(), run.getSummary());
        return CompletableFuture.completedFuture(run);
    }

    private double round(double v) { return Math.round(v * 1000.0) / 1000.0; }

    private EvalResult evaluateItem(EvalItem item, String runType, String runId, String userId) {
        EvalResult result = new EvalResult();
        result.setRunId(runId);
        result.setItemId(item.getId());
        long start = System.currentTimeMillis();

        if ("rag".equals(runType) || "full".equals(runType)) {
            evaluateRag(item, result, userId);
        }
        if ("agent".equals(runType) || "full".equals(runType)) {
            evaluateAgent(item, result, userId);
        }

        result.setLatencyMs((int)(System.currentTimeMillis() - start));
        return result;
    }

    // ══════════════════════════════════════════════════════
    //  RAG 评估（RAGAS 方法论）
    // ══════════════════════════════════════════════════════

    /**
     * RAG 评估 —— 委托给 RAGAS Python 微服务（标准 RAGAS 框架）
     *
     * 指标由 RAGAS 计算：Faithfulness, ResponseRelevancy,
     * ContextPrecision, ContextRecall, FactualCorrectness
     */
    private void evaluateRag(EvalItem item, EvalResult result, String userId) {
        try {
            // Step 1: 检索 context
            var ragResults = ragService.searchWithMinScore(item.getQuestion(), 5, 0.3, userId);
            List<String> contexts = ragResults.stream()
                    .map(n -> n.getTitle() + ": " + (n.getContent() != null ? n.getContent().replaceAll("<[^>]*>", "") : ""))
                    .toList();

            // Step 2: 用 Agent 基于 context 生成回答
            String prompt = "Based on the following retrieved contexts, answer the question concisely.\n\n" +
                "Contexts:\n" + String.join("\n---\n", contexts) + "\n\nQuestion: " + item.getQuestion();
            var chatResponse = agentService.chat(prompt, List.of(), userId);
            String answer = chatResponse.getContent();
            result.setActualAnswer(answer);
            try { result.setActualContexts(objectMapper.writeValueAsString(contexts)); }
            catch (Exception e) { log.warn("Failed to serialize actualContexts: {}", e.getMessage()); }

            // Step 3: 调用 RAGAS 微服务
            Map<String, Object> ragasReq = new LinkedHashMap<>();
            ragasReq.put("question", item.getQuestion());
            ragasReq.put("answer", answer);
            ragasReq.put("contexts", contexts);
            if (item.getExpectedAnswer() != null && !item.getExpectedAnswer().isEmpty()) {
                ragasReq.put("expected_answer", item.getExpectedAnswer());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String reqBody = objectMapper.writeValueAsString(ragasReq);
            HttpEntity<String> entity = new HttpEntity<>(reqBody, headers);

            var response = restTemplate.postForObject(
                    ragasUrl + "/evaluate/rag", entity, Map.class);

            if (response != null) {
                if (response.get("error") != null) {
                    log.error("RAGAS service error: {}", response.get("error"));
                    return;
                }
                result.setFaithfulness(toDouble(response.get("faithfulness")));
                result.setAnswerRelevancy(toDouble(response.get("answer_relevancy")));
                result.setContextPrecision(toDouble(response.get("context_precision")));
                result.setContextRecall(toDouble(response.get("context_recall")));

                // factual_correctness 存入 evaluationDetails
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("source", "ragas");
                if (response.get("factual_correctness") != null) {
                    details.put("factualCorrectness", response.get("factual_correctness"));
                }
                try { result.setEvaluationDetails(objectMapper.writeValueAsString(details)); }
                catch (Exception e) { log.warn("Failed to serialize evaluationDetails: {}", e.getMessage()); }

                log.info("RAGAS eval: faithfulness={}, relevancy={}, precision={}, recall={}",
                        result.getFaithfulness(), result.getAnswerRelevancy(),
                        result.getContextPrecision(), result.getContextRecall());
            }
        } catch (Exception e) {
            log.error("RAG evaluation error for item {}: {}", item.getId(), e.getMessage(), e);
        }
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    // ══════════════════════════════════════════════════════
    //  Agent 评估 —— 委托给 RAGAS Python 微服务
    //
    //  指标由 RAGAS 计算：ToolCallAccuracy, AgentGoalAccuracy, TopicAdherence
    //  效率数据（token/latency）从 agent_traces 表获取
    // ══════════════════════════════════════════════════════

    private void evaluateAgent(EvalItem item, EvalResult result, String userId) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();

            // Step 1: 真实调用 Agent
            LocalDateTime beforeCall = LocalDateTime.now();
            var response = agentService.chat(item.getQuestion(), List.of(), userId);
            result.setActualAnswer(response.getContent());

            // Step 2: 从 agent_traces 获取真实工具调用 + 效率数据
            List<Map<String, Object>> actualToolCalls = new ArrayList<>();
            int traceTokens = 0;
            int traceLatency = 0;
            try {
                Thread.sleep(500); // 等待 trace 异步写入
                var traces = agentTraceRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId, beforeCall.minusSeconds(5), PageRequest.of(0, 3));
                if (!traces.isEmpty()) {
                    AgentTrace trace = traces.get(0);
                    traceTokens = trace.getTotalTokens() != null ? trace.getTotalTokens() : 0;
                    traceLatency = trace.getLatencyMs() != null ? trace.getLatencyMs() : 0;
                    details.put("traceId", trace.getTraceId());

                    // 解析 tools_called JSONB → [{name, args}, ...]
                    if (trace.getToolsCalled() != null && !trace.getToolsCalled().isEmpty()) {
                        try {
                            var toolsNode = objectMapper.readTree(trace.getToolsCalled());
                            if (toolsNode.isArray()) {
                                for (var node : toolsNode) {
                                    Map<String, Object> tc = new LinkedHashMap<>();
                                    tc.put("name", node.has("name") ? node.get("name").asText()
                                            : node.has("tool") ? node.get("tool").asText() : node.asText());
                                    if (node.has("args")) {
                                        tc.put("args", objectMapper.convertValue(node.get("args"), Map.class));
                                    }
                                    actualToolCalls.add(tc);
                                }
                            }
                        } catch (Exception e) {
                            for (String t : trace.getToolsCalled().split(",")) {
                                actualToolCalls.add(Map.of("name", t.trim()));
                            }
                        }
                    }
                    result.setActualToolCalls(trace.getToolsCalled());
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve agent trace: {}", e.getMessage());
            }
            result.setTotalTokens(traceTokens);
            details.put("totalTokens", traceTokens);
            details.put("traceLatencyMs", traceLatency);

            // Step 3: 构建 RAGAS Agent 评估请求
            List<Map<String, Object>> expectedToolCalls = new ArrayList<>();
            if (item.getExpectedToolCalls() != null && !item.getExpectedToolCalls().isEmpty()) {
                for (String t : item.getExpectedToolCalls().split(",")) {
                    expectedToolCalls.add(Map.of("name", t.trim()));
                }
            }

            Map<String, Object> ragasReq = new LinkedHashMap<>();
            ragasReq.put("question", item.getQuestion());
            ragasReq.put("answer", response.getContent());
            ragasReq.put("actual_tool_calls", actualToolCalls);
            ragasReq.put("expected_tool_calls", expectedToolCalls);
            if (item.getExpectedAnswer() != null && !item.getExpectedAnswer().isEmpty()) {
                ragasReq.put("expected_answer", item.getExpectedAnswer());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String reqBody = objectMapper.writeValueAsString(ragasReq);
            HttpEntity<String> entity = new HttpEntity<>(reqBody, headers);

            var ragasResponse = restTemplate.postForObject(
                    ragasUrl + "/evaluate/agent", entity, Map.class);

            if (ragasResponse != null) {
                if (ragasResponse.get("error") != null) {
                    log.error("RAGAS agent eval error: {}", ragasResponse.get("error"));
                    details.put("ragasError", ragasResponse.get("error"));
                } else {
                    // ToolCallAccuracy → toolAccuracy
                    result.setToolAccuracy(toDouble(ragasResponse.get("tool_call_accuracy")));
                    // AgentGoalAccuracy → taskCompletion
                    result.setTaskCompletion(toDouble(ragasResponse.get("agent_goal_accuracy")));
                    // TopicAdherence → consistency（复用字段）
                    result.setConsistency(toDouble(ragasResponse.get("topic_adherence")));
                    // ErrorRecoveryRate
                    result.setErrorRecoveryRate(toDouble(ragasResponse.get("error_recovery_rate")));

                    details.put("source", "ragas");
                }
            }

            try { result.setEvaluationDetails(objectMapper.writeValueAsString(details)); }
            catch (Exception e) { log.warn("Failed to serialize evaluationDetails: {}", e.getMessage()); }

            log.info("Agent eval (RAGAS) for item {}: goalAcc={}, toolAcc={}, topicAdh={}, errRecovery={}, tokens={}, latency={}ms",
                    item.getId(), result.getTaskCompletion(), result.getToolAccuracy(),
                    result.getConsistency(), result.getErrorRecoveryRate(), traceTokens, traceLatency);

        } catch (Exception e) {
            log.error("Agent evaluation error for item {}: {}", item.getId(), e.getMessage(), e);
        }
    }

    // ── Run queries ──

    public List<EvalRun> listRuns() {
        String userId = securityUtils.getCurrentUserId();
        return runRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    public EvalRun getRun(String id) {
        String userId = securityUtils.getCurrentUserId();
        return runRepository.findByIdAndUserId(id, userId).orElseThrow();
    }

    public List<EvalResult> getResults(String runId) {
        String userId = securityUtils.getCurrentUserId();
        runRepository.findByIdAndUserId(runId, userId).orElseThrow();
        return resultRepository.findByRunId(runId);
    }

    private EvalDataset getDatasetForUser(String datasetId, String userId) {
        return datasetRepository.findByIdAndUserId(datasetId, userId).orElseThrow();
    }
}
