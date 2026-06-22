package com.ainote.app.agent.pipeline;

import com.ainote.app.agent.tools.ToolLoopDetector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PreExecutionGuard 前置守卫测试")
class PreExecutionGuardTest {

    private PreExecutionGuard guard;
    private ToolLoopDetector loopDetector;
    private IdempotencyStore idempotencyStore;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        loopDetector = new ToolLoopDetector(3); // 3次重复即触发
        idempotencyStore = new IdempotencyStore();
        guard = new PreExecutionGuard(loopDetector, idempotencyStore);
    }

    @AfterEach
    void tearDown() {
        loopDetector.reset();
    }

    // ---- 参数校验 ----

    @Test
    @DisplayName("noteAction:update 缺少 noteId 应被拦截")
    void shouldBlockMissingRequiredParam() {
        ObjectNode params = mapper.createObjectNode();
        params.put("title", "新标题");
        // 缺少 noteId

        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", "noteAction", "update", params);
        GuardResult result = guard.check(ctx);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("noteId");
    }

    @Test
    @DisplayName("noteAction:update noteId 为空白应被拦截")
    void shouldBlockBlankRequiredParam() {
        ObjectNode params = mapper.createObjectNode();
        params.put("noteId", "   ");

        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", "noteAction", "update", params);
        GuardResult result = guard.check(ctx);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("noteId");
    }

    @Test
    @DisplayName("noteAction:addTag 缺少 tagName 应被拦截")
    void shouldBlockMissingSecondParam() {
        ObjectNode params = mapper.createObjectNode();
        params.put("noteId", "note-1");
        // 缺少 tagName

        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", "noteAction", "addTag", params);
        GuardResult result = guard.check(ctx);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("tagName");
    }

    @Test
    @DisplayName("scheduleAction:create 缺少 startTime 应被拦截")
    void shouldBlockMissingScheduleParam() {
        ObjectNode params = mapper.createObjectNode();
        params.put("title", "开会");

        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", "scheduleAction", "create", params);
        GuardResult result = guard.check(ctx);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("startTime");
    }

    @Test
    @DisplayName("参数完整时应通过")
    void shouldPassWithCompleteParams() {
        ObjectNode params = mapper.createObjectNode();
        params.put("noteId", "note-1");
        params.put("tagName", "重要");

        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", "noteAction", "addTag", params);
        GuardResult result = guard.check(ctx);

        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    @DisplayName("未注册的 action（如 listAll）应直接通过，不做参数校验")
    void shouldPassUnregisteredAction() {
        ObjectNode params = mapper.createObjectNode();

        ToolExecutionContext ctx = new ToolExecutionContext("req1", "user1", "noteAction", "listAll", params);
        GuardResult result = guard.check(ctx);

        assertThat(result.isBlocked()).isFalse();
    }

    // ---- 循环检测 ----

    @Test
    @DisplayName("连续调用超过阈值应触发循环检测")
    void shouldDetectLoop() {
        ObjectNode params = mapper.createObjectNode();
        params.put("query", "测试");

        // 用读操作（search）测循环检测，避免幂等检测干扰
        // 前3次应该通过
        for (int i = 0; i < 3; i++) {
            ToolExecutionContext ctx = new ToolExecutionContext("req" + i, "user1", "noteAction", "search", params);
            GuardResult result = guard.check(ctx);
            assertThat(result.isBlocked()).isFalse();
        }

        // 第4次应触发（阈值=3，第4次 count=4 > 3）
        ToolExecutionContext ctx = new ToolExecutionContext("req4", "user1", "noteAction", "search", params);
        GuardResult result = guard.check(ctx);
        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("重复调用");
    }

    @Test
    @DisplayName("不同参数的调用不应触发循环检测")
    void shouldNotDetectLoopWithDifferentParams() {
        for (int i = 0; i < 5; i++) {
            ObjectNode params = mapper.createObjectNode();
            params.put("noteId", "note-" + i); // 每次不同的 noteId

            ToolExecutionContext ctx = new ToolExecutionContext("req" + i, "user1", "noteAction", "update", params);
            GuardResult result = guard.check(ctx);
            assertThat(result.isBlocked()).isFalse();
        }
    }

    // ---- 幂等检测 ----

    @Test
    @DisplayName("写操作重复执行应被幂等拦截")
    void shouldBlockDuplicateMutatingAction() {
        ObjectNode params = mapper.createObjectNode();
        params.put("title", "新笔记");
        params.put("content", "内容");

        // 第一次应通过
        ToolExecutionContext ctx1 = new ToolExecutionContext("req1", "user1", "noteAction", "create", params);
        GuardResult result1 = guard.check(ctx1);
        assertThat(result1.isBlocked()).isFalse();

        // 重置 loopDetector 避免循环检测干扰
        loopDetector.reset();

        // 第二次应被幂等拦截
        ToolExecutionContext ctx2 = new ToolExecutionContext("req2", "user1", "noteAction", "create", params);
        GuardResult result2 = guard.check(ctx2);
        assertThat(result2.isBlocked()).isTrue();
        assertThat(result2.getReason()).contains("已执行过");
    }

    @Test
    @DisplayName("读操作不应被幂等检测拦截")
    void shouldNotBlockReadAction() {
        ObjectNode params = mapper.createObjectNode();
        params.put("query", "机器学习");

        // 多次搜索应全部通过
        for (int i = 0; i < 3; i++) {
            loopDetector.reset();
            ToolExecutionContext ctx = new ToolExecutionContext("req" + i, "user1", "noteAction", "search", params);
            GuardResult result = guard.check(ctx);
            assertThat(result.isBlocked()).isFalse();
        }
    }

    @Test
    @DisplayName("不同用户的相同操作不应被幂等拦截")
    void shouldNotBlockDifferentUsers() {
        ObjectNode params = mapper.createObjectNode();
        params.put("title", "笔记");
        params.put("content", "内容");

        ToolExecutionContext ctx1 = new ToolExecutionContext("req1", "user1", "noteAction", "create", params);
        guard.check(ctx1);

        loopDetector.reset();

        ToolExecutionContext ctx2 = new ToolExecutionContext("req2", "user2", "noteAction", "create", params);
        GuardResult result2 = guard.check(ctx2);
        assertThat(result2.isBlocked()).isFalse();
    }
}
