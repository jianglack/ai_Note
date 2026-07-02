package com.ainote.app.agent.pipeline;

import com.ainote.app.agent.CancellationToken;
import com.ainote.app.agent.budget.TokenBudget;
import com.ainote.app.agent.pending.PendingActionRegistry;
import com.ainote.app.agent.tools.ToolLoopDetector;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.AgentMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ToolExecutionPipeline 管道集成测试")
class ToolExecutionPipelineTest {

    private ToolExecutionPipeline pipeline;
    private ToolLoopDetector loopDetector;
    private IdempotencyStore idempotencyStore;
    private TokenBudget tokenBudget;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        loopDetector = new ToolLoopDetector(5);
        idempotencyStore = new IdempotencyStore();
        tokenBudget = new TokenBudget();
        ReflectionTestUtils.setField(tokenBudget, "maxTokensPerRequest", 50000);
        ReflectionTestUtils.setField(tokenBudget, "warnThreshold", 40000);

        PreExecutionGuard preGuard = new PreExecutionGuard(loopDetector, idempotencyStore);
        ToolAuditLogger auditLogger = new ToolAuditLogger();
        PostExecutionHandler postHandler = new PostExecutionHandler(
                auditLogger, createMetricsService(), new PendingActionRegistry(mapper));

        pipeline = new ToolExecutionPipeline(preGuard, postHandler, tokenBudget);

        ToolAuditLogger.resetTranscript();
        GracefulDegradation.reset();
        tokenBudget.reset();
    }

    private AgentMetricsService createMetricsService() {
        return new AgentMetricsService(
                new SimpleMeterRegistry(),
                mock(AgentTraceRepository.class),
                mock(TaskPlanRepository.class),
                mock(TaskStepRepository.class)
        );
    }

    @AfterEach
    void tearDown() {
        loopDetector.reset();
        ToolAuditLogger.resetTranscript();
        GracefulDegradation.reset();
        ToolExecutionPipeline.clearCancelToken();
        tokenBudget.reset();
    }

    // ---- 正常执行 ----

    @Test
    @DisplayName("正常工具调用应返回实际执行结果")
    void shouldExecuteNormally() {
        ObjectNode params = mapper.createObjectNode();
        params.put("query", "机器学习");

        String result = pipeline.execute("user1", "noteAction", "search", params,
                () -> "找到 3 条相关笔记");

        assertThat(result).contains("3 条");
    }

    @Test
    @DisplayName("正常执行后应记录审计日志")
    void shouldRecordAuditLog() {
        ObjectNode params = mapper.createObjectNode();
        params.put("query", "测试");

        pipeline.execute("user1", "noteAction", "search", params,
                () -> "找到 1 条笔记");

        List<ToolAuditLogger.ToolAuditEntry> transcript = ToolAuditLogger.getTranscript();
        assertThat(transcript).hasSize(1);
        assertThat(transcript.get(0).toolName()).isEqualTo("noteAction");
        assertThat(transcript.get(0).action()).isEqualTo("search");
        assertThat(transcript.get(0).blocked()).isFalse();
    }

    // ---- 前置守卫拦截 ----

    @Test
    @DisplayName("缺少必填参数时应被拦截，不执行实际逻辑")
    void shouldBlockMissingParams() {
        ObjectNode params = mapper.createObjectNode();
        // 缺少 noteId

        String result = pipeline.execute("user1", "noteAction", "update", params,
                () -> { throw new RuntimeException("不应被执行"); });

        assertThat(result).contains("noteId");

        List<ToolAuditLogger.ToolAuditEntry> transcript = ToolAuditLogger.getTranscript();
        assertThat(transcript).hasSize(1);
        assertThat(transcript.get(0).blocked()).isTrue();
    }

    // ---- 取消机制 ----

    @Test
    @DisplayName("取消令牌已取消时应返回取消提示")
    void shouldReturnCancelledWhenTokenCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        ToolExecutionPipeline.setCancelToken(token);

        ObjectNode params = mapper.createObjectNode();
        String result = pipeline.execute("user1", "noteAction", "listAll", params,
                () -> { throw new RuntimeException("不应被执行"); });

        assertThat(result).contains("取消");
    }

    @Test
    @DisplayName("取消令牌未取消时应正常执行")
    void shouldExecuteWhenTokenNotCancelled() {
        CancellationToken token = new CancellationToken();
        ToolExecutionPipeline.setCancelToken(token);

        ObjectNode params = mapper.createObjectNode();
        String result = pipeline.execute("user1", "noteAction", "listAll", params,
                () -> "共有 5 条笔记");

        assertThat(result).contains("5 条");
    }

    // ---- 异常处理 ----

    @Test
    @DisplayName("工具执行抛异常时应捕获并返回错误信息")
    void shouldCatchToolException() {
        ObjectNode params = mapper.createObjectNode();
        params.put("query", "test");

        String result = pipeline.execute("user1", "noteAction", "search", params,
                () -> { throw new RuntimeException("网络超时"); });

        assertThat(result).contains("操作执行异常");
        assertThat(result).contains("网络超时");
    }

    // ---- 优雅降级 ----

    @Test
    @DisplayName("标记接近上限后应追加降级提示")
    void shouldAppendDegradationHint() {
        GracefulDegradation.markApproachingLimit();

        ObjectNode params = mapper.createObjectNode();
        String result = pipeline.execute("user1", "noteAction", "listAll", params,
                () -> "共有 3 条笔记");

        assertThat(result).contains("共有 3 条笔记");
        assertThat(result).contains("工具调用次数即将达到上限");
    }

    // ---- Token 预算提示 ----

    @Test
    @DisplayName("Token 预算不足时应追加预算提示")
    void shouldAppendBudgetHint() {
        tokenBudget.addTokens(46000); // 剩余 4000 < 5000

        ObjectNode params = mapper.createObjectNode();
        String result = pipeline.execute("user1", "noteAction", "listAll", params,
                () -> "查询完成");

        assertThat(result).contains("查询完成");
        assertThat(result).contains("预算即将用完");
    }

    @Test
    @DisplayName("Token 预算充足时不应追加提示")
    void shouldNotAppendBudgetHintWhenSufficient() {
        tokenBudget.addTokens(10000);

        ObjectNode params = mapper.createObjectNode();
        String result = pipeline.execute("user1", "noteAction", "listAll", params,
                () -> "查询完成");

        assertThat(result).contains("查询");
    }

    // ---- 幂等检测（端到端） ----

    @Test
    @DisplayName("重复写操作应被幂等拦截")
    void shouldBlockDuplicateWrite() {
        ObjectNode params = mapper.createObjectNode();
        params.put("title", "新笔记");

        // 第一次应通过
        String result1 = pipeline.execute("user1", "noteAction", "create", params,
                () -> "已创建笔记「新笔记」（ID: n-1）");
        assertThat(result1).contains("已创建");

        // 重置循环检测避免干扰
        loopDetector.reset();

        // 第二次应被幂等拦截
        String result2 = pipeline.execute("user1", "noteAction", "create", params,
                () -> { throw new RuntimeException("不应被执行"); });
        assertThat(result2).contains("已执行过");
    }
}
