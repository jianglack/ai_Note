package com.ainote.app.agent.pipeline;

import com.ainote.app.agent.pending.PendingActionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.AgentMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("PostExecutionHandler 后置处理器测试")
class PostExecutionHandlerTest {

    private PostExecutionHandler handler;
    private ToolAuditLogger auditLogger;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        auditLogger = new ToolAuditLogger();
        handler = new PostExecutionHandler(auditLogger, createMetricsService(), new PendingActionRegistry(mapper));
        ToolAuditLogger.resetTranscript();
    }

    @AfterEach
    void tearDown() {
        ToolAuditLogger.resetTranscript();
    }

    private ToolExecutionContext createContext(String toolName, String action, String rawResult) {
        ObjectNode params = mapper.createObjectNode();
        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", toolName, action, params);
        ctx.setGuardResult(GuardResult.passed());
        ctx.setRawResult(rawResult);
        return ctx;
    }

    private AgentMetricsService createMetricsService() {
        return new AgentMetricsService(
                new SimpleMeterRegistry(),
                mock(AgentTraceRepository.class),
                mock(TaskPlanRepository.class),
                mock(TaskStepRepository.class)
        );
    }

    // ---- 成功解析 ----

    @Test
    @DisplayName("正常成功结果应解析为 SUCCESS")
    void shouldParseSuccessResult() {
        ToolExecutionContext ctx = createContext("noteAction", "create",
                "已创建笔记「会议记录」（ID: abc-123）。");
        handler.handle(ctx);

        assertThat(ctx.getOutcome()).isNotNull();
        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.SUCCESS);
        assertThat(ctx.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("应从结果中提取实体 ID")
    void shouldExtractEntityId() {
        ToolExecutionContext ctx = createContext("noteAction", "create",
                "已创建笔记「测试」（ID: note-abc-123）。");
        handler.handle(ctx);

        assertThat(ctx.getOutcome().entityId()).isEqualTo("note-abc-123");
        assertThat(ctx.getOutcome().entityType()).isEqualTo("NOTE");
    }

    // ---- 失败解析 ----

    @Test
    @DisplayName("含「失败」关键词应解析为 FAILED")
    void shouldParseFailedResult() {
        ToolExecutionContext ctx = createContext("noteAction", "create",
                "创建笔记失败：标题不能为空");
        handler.handle(ctx);

        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.FAILED);
    }

    @Test
    @DisplayName("含「不存在」关键词应解析为 FAILED")
    void shouldParseNotExistAsFailed() {
        ToolExecutionContext ctx = createContext("noteAction", "update",
                "该笔记不存在，请检查 ID");
        handler.handle(ctx);

        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.FAILED);
    }

    @Test
    @DisplayName("搜索结果中的「未找到」应视为成功（空结果）")
    void shouldParseSearchNotFoundAsSuccess() {
        ToolExecutionContext ctx = createContext("noteAction", "search",
                "未找到与「量子计算」相关的笔记");
        handler.handle(ctx);

        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.SUCCESS);
    }

    @Test
    @DisplayName("null 结果应解析为 FAILED")
    void shouldParseNullResultAsFailed() {
        ToolExecutionContext ctx = createContext("noteAction", "create", null);
        handler.handle(ctx);

        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.FAILED);
        assertThat(ctx.getOutcome().message()).contains("未返回结果");
    }

    // ---- PENDING_ACTION 解析 ----

    @Test
    @DisplayName("PENDING_ACTION 应解析为 PENDING_CONFIRM")
    void shouldParsePendingAction() {
        String result = "PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"noteId\":\"note-1\"}\n确认删除吗？";
        ToolExecutionContext ctx = createContext("noteAction", "delete", result);
        handler.handle(ctx);

        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.PENDING_CONFIRM);
    }

    @Test
    @DisplayName("未知 PENDING_ACTION 类型不应被审计为待确认")
    void shouldRejectUnknownPendingActionType() {
        String result = "PENDING_ACTION:{\"type\":\"FORMAT_DISK\",\"noteId\":\"note-1\"}\n确认执行吗？";
        ToolExecutionContext ctx = createContext("noteAction", "delete", result);
        handler.handle(ctx);

        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.FAILED);
        assertThat(ctx.getOutcome().message()).contains("Invalid PENDING_ACTION");
    }

    @Test
    @DisplayName("缺少必需字段的 PENDING_ACTION 不应被审计为待确认")
    void shouldRejectPendingActionMissingRequiredField() {
        String result = "PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"title\":\"缺少 ID\"}\n确认删除吗？";
        ToolExecutionContext ctx = createContext("noteAction", "delete", result);
        handler.handle(ctx);

        assertThat(ctx.getOutcome().status()).isEqualTo(ToolOutcome.Status.FAILED);
        assertThat(ctx.getOutcome().message()).contains("Invalid PENDING_ACTION");
    }

    // ---- 副作用记录 ----

    @Test
    @DisplayName("写操作成功后应记录副作用")
    void shouldRecordSideEffectForMutatingAction() {
        ToolExecutionContext ctx = createContext("noteAction", "create",
                "已创建笔记「测试」（ID: note-1）。");
        handler.handle(ctx);

        assertThat(ctx.getSideEffect()).isNotNull();
        assertThat(ctx.getSideEffect().action()).isEqualTo("CREATE");
        assertThat(ctx.getSideEffect().entityType()).isEqualTo("NOTE");
        assertThat(ctx.getSideEffect().reversible()).isTrue();
    }

    @Test
    @DisplayName("永久删除操作应标记为不可逆")
    void shouldMarkIrreversibleAction() {
        ToolExecutionContext ctx = createContext("noteAction", "confirmPermanentDelete",
                "笔记已永久删除");
        handler.handle(ctx);

        assertThat(ctx.getSideEffect()).isNotNull();
        assertThat(ctx.getSideEffect().action()).isEqualTo("DELETE");
        assertThat(ctx.getSideEffect().reversible()).isFalse();
    }

    @Test
    @DisplayName("读操作不应记录副作用")
    void shouldNotRecordSideEffectForReadAction() {
        ToolExecutionContext ctx = createContext("noteAction", "search",
                "找到 3 条相关笔记");
        handler.handle(ctx);

        assertThat(ctx.getSideEffect()).isNull();
    }

    // ---- 实体类型推断 ----

    @Test
    @DisplayName("scheduleAction 应推断为 SCHEDULE 实体类型")
    void shouldInferScheduleEntityType() {
        ToolExecutionContext ctx = createContext("scheduleAction", "create",
                "已创建日程「开会」（ID: sch-1）。");
        handler.handle(ctx);

        assertThat(ctx.getOutcome().entityType()).isEqualTo("SCHEDULE");
    }

    @Test
    @DisplayName("folderAction 应推断为 FOLDER 实体类型")
    void shouldInferFolderEntityType() {
        ToolExecutionContext ctx = createContext("folderAction", "create",
                "已创建文件夹「工作」（ID: folder-1）。");
        handler.handle(ctx);

        assertThat(ctx.getOutcome().entityType()).isEqualTo("FOLDER");
    }

    // ---- 审计日志 ----

    @Test
    @DisplayName("处理后应在 transcript 中记录审计条目")
    void shouldLogToTranscript() {
        ToolExecutionContext ctx = createContext("noteAction", "create",
                "已创建笔记「测试」（ID: note-1）。");
        handler.handle(ctx);

        List<ToolAuditLogger.ToolAuditEntry> transcript = ToolAuditLogger.getTranscript();
        assertThat(transcript).hasSize(1);

        ToolAuditLogger.ToolAuditEntry entry = transcript.get(0);
        assertThat(entry.toolName()).isEqualTo("noteAction");
        assertThat(entry.action()).isEqualTo("create");
        assertThat(entry.status()).isEqualTo("SUCCESS");
        assertThat(entry.blocked()).isFalse();
        assertThat(entry.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("被拦截的请求也应记录审计条目")
    void shouldLogBlockedToTranscript() {
        ObjectNode params = mapper.createObjectNode();
        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", "noteAction", "create", params);
        ctx.setGuardResult(GuardResult.blocked("参数缺失"));
        ctx.setRawResult(null);
        handler.handle(ctx);

        List<ToolAuditLogger.ToolAuditEntry> transcript = ToolAuditLogger.getTranscript();
        assertThat(transcript).hasSize(1);
        assertThat(transcript.get(0).blocked()).isTrue();
        assertThat(transcript.get(0).blockReason()).isEqualTo("参数缺失");
    }
}
