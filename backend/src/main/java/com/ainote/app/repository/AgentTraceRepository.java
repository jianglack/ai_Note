package com.ainote.app.repository;

import com.ainote.app.entity.AgentTrace;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 追踪记录 Repository
 */
@Repository
public interface AgentTraceRepository extends JpaRepository<AgentTrace, String>, JpaSpecificationExecutor<AgentTrace> {

    /**
     * 按用户ID查询追踪记录（分页，按时间倒序）
     */
    List<AgentTrace> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 按用户ID和时间范围查询
     */
    List<AgentTrace> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String userId, LocalDateTime start, LocalDateTime end);

    default List<AgentTrace> findFilteredTraces(
            String userId,
            LocalDateTime start,
            LocalDateTime end,
            String model,
            Pageable pageable) {
        Specification<AgentTrace> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            if (model != null && !model.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("model"), model));
            }
            query.orderBy(criteriaBuilder.desc(root.get("createdAt")));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return findAll(spec, pageable).getContent();
    }

    /**
     * 统计用户的总 token 消耗
     */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM AgentTrace t WHERE t.userId = :userId")
    Long sumTotalTokensByUserId(String userId);

    /**
     * 统计用户指定时间范围内的 token 消耗
     */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM AgentTrace t " +
           "WHERE t.userId = :userId AND t.createdAt BETWEEN :start AND :end")
    Long sumTotalTokensByUserIdAndDateRange(String userId, LocalDateTime start, LocalDateTime end);

    /**
     * 统计用户的调用次数
     */
    long countByUserId(String userId);

    /**
     * 统计用户指定时间范围内的调用次数
     */
    long countByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);

    /**
     * 按 traceId 查询
     */
    List<AgentTrace> findByTraceId(String traceId);

    /**
     * 查询用户在指定时间之后的最近追踪记录
     */
    List<AgentTrace> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String userId, LocalDateTime after, Pageable pageable);

    /**
     * 按天聚合成本（最近 N 天）
     */
    @Query(value = "SELECT CAST(created_at AS DATE) as day, " +
            "COALESCE(SUM(estimated_cost_yuan), 0) as total_cost, " +
            "COUNT(*) as call_count, " +
            "COALESCE(SUM(total_tokens), 0) as total_tokens " +
            "FROM agent_traces WHERE created_at >= :since " +
            "GROUP BY CAST(created_at AS DATE) ORDER BY day DESC", nativeQuery = true)
    List<Object[]> aggregateCostByDay(LocalDateTime since);

    /**
     * 按模型聚合成本
     */
    @Query(value = "SELECT model, " +
            "COALESCE(SUM(estimated_cost_yuan), 0) as total_cost, " +
            "COUNT(*) as call_count, " +
            "COALESCE(SUM(total_tokens), 0) as total_tokens " +
            "FROM agent_traces WHERE created_at >= :since " +
            "GROUP BY model ORDER BY total_cost DESC", nativeQuery = true)
    List<Object[]> aggregateCostByModel(LocalDateTime since);

    /**
     * 最贵的请求 Top N
     */
    @Query("SELECT t FROM AgentTrace t WHERE t.createdAt >= :since " +
            "ORDER BY t.estimatedCostYuan DESC")
    List<AgentTrace> findTopByCost(LocalDateTime since, Pageable pageable);

    /**
     * 按状态统计（指定时间范围）
     */
    long countByCreatedAtAfterAndStatus(LocalDateTime since, String status);

    /**
     * 统计总调用次数（指定时间范围）
     */
    long countByCreatedAtAfter(LocalDateTime since);

    /**
     * 按工具聚合调用指标（近 N 小时）
     * 返回: toolName, totalCalls, failureCalls, avgLatencyMs
     */
    @Query(value = """
            SELECT
                tool_entry->>'name' AS tool_name,
                COUNT(*) AS total_calls,
                SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS failure_calls,
                COALESCE(AVG(latency_ms), 0) AS avg_latency_ms,
                MAX(CASE WHEN status = 'ERROR' THEN created_at END) AS last_failure_time
            FROM agent_traces,
                 jsonb_array_elements(COALESCE(tools_called, '[]'::jsonb)) AS tool_entry
            WHERE created_at >= :since
            GROUP BY tool_entry->>'name'
            ORDER BY total_calls DESC
            """, nativeQuery = true)
    List<Object[]> aggregateToolMetrics(LocalDateTime since);
}
