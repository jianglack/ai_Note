package com.ainote.app.agent.pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GracefulDegradation 优雅降级测试")
class GracefulDegradationTest {

    @AfterEach
    void tearDown() {
        GracefulDegradation.reset();
    }

    @Test
    @DisplayName("未标记时不应追加提示")
    void shouldNotAppendWhenNotMarked() {
        String result = GracefulDegradation.appendHintIfNeeded("操作成功");
        assertThat(result).isEqualTo("操作成功");
    }

    @Test
    @DisplayName("标记后应追加降级提示")
    void shouldAppendAfterMark() {
        GracefulDegradation.markApproachingLimit();
        String result = GracefulDegradation.appendHintIfNeeded("操作成功");
        assertThat(result).contains("操作成功");
        assertThat(result).contains("工具调用次数即将达到上限");
    }

    @Test
    @DisplayName("reset 后不应再追加提示")
    void shouldNotAppendAfterReset() {
        GracefulDegradation.markApproachingLimit();
        GracefulDegradation.reset();
        String result = GracefulDegradation.appendHintIfNeeded("操作成功");
        assertThat(result).isEqualTo("操作成功");
    }

    @Test
    @DisplayName("多次标记不应重复追加多条提示")
    void shouldNotDuplicateHint() {
        GracefulDegradation.markApproachingLimit();
        GracefulDegradation.markApproachingLimit();
        String result = GracefulDegradation.appendHintIfNeeded("OK");
        // 只追加一次提示
        long count = result.chars().filter(c -> c == '系').count();
        assertThat(count).isEqualTo(1);
    }
}
