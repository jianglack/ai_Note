package com.ainote.app.controller;

import com.ainote.app.entity.AgentTrace;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.security.AdminAccessGuard;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 成本统计 API
 * 提供按天、按模型的费用聚合和最贵请求查询
 */
@RestController
@RequestMapping("/api/admin")
public class CostController {

    private final AgentTraceRepository traceRepository;
    private final AdminAccessGuard adminAccessGuard;

    public CostController(AgentTraceRepository traceRepository, AdminAccessGuard adminAccessGuard) {
        this.traceRepository = traceRepository;
        this.adminAccessGuard = adminAccessGuard;
    }

    /**
     * 按天聚合费用
     */
    @GetMapping("/cost-stats")
    public ResponseEntity<List<Map<String, Object>>> costByDay(
            @RequestParam(defaultValue = "30") int days) {
        adminAccessGuard.checkAdminAccess();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = traceRepository.aggregateCostByDay(since);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", row[0] != null ? row[0].toString() : null);
            item.put("totalCost", row[1] != null ? ((Number) row[1]).doubleValue() : 0);
            item.put("callCount", row[2] != null ? ((Number) row[2]).longValue() : 0);
            item.put("totalTokens", row[3] != null ? ((Number) row[3]).longValue() : 0);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 按模型聚合费用
     */
    @GetMapping("/cost-by-model")
    public ResponseEntity<List<Map<String, Object>>> costByModel(
            @RequestParam(defaultValue = "30") int days) {
        adminAccessGuard.checkAdminAccess();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = traceRepository.aggregateCostByModel(since);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("model", row[0] != null ? row[0].toString() : "unknown");
            item.put("totalCost", row[1] != null ? ((Number) row[1]).doubleValue() : 0);
            item.put("callCount", row[2] != null ? ((Number) row[2]).longValue() : 0);
            item.put("totalTokens", row[3] != null ? ((Number) row[3]).longValue() : 0);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 最贵的请求
     */
    @GetMapping("/cost-top")
    public ResponseEntity<List<Map<String, Object>>> topCost(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days) {
        adminAccessGuard.checkAdminAccess();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AgentTrace> traces = traceRepository.findTopByCost(since, PageRequest.of(0, limit));

        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentTrace t : traces) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("traceId", t.getTraceId());
            item.put("userId", t.getUserId());
            item.put("model", t.getModel());
            item.put("callType", t.getCallType());
            item.put("inputTokens", t.getInputTokens());
            item.put("outputTokens", t.getOutputTokens());
            item.put("estimatedCostYuan", t.getEstimatedCostYuan());
            item.put("latencyMs", t.getLatencyMs());
            item.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }
}
