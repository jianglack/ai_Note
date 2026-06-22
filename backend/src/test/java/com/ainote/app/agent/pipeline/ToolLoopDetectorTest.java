package com.ainote.app.agent.pipeline;

import com.ainote.app.agent.tools.ToolLoopDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolLoopDetector 循环检测器测试")
class ToolLoopDetectorTest {

    private ToolLoopDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ToolLoopDetector(3);
    }

    @AfterEach
    void tearDown() {
        detector.reset();
    }

    @Test
    @DisplayName("首次调用不应触发循环检测")
    void shouldNotDetectFirstCall() {
        boolean isLoop = detector.recordAndCheck("noteAction", "create", "param1");
        assertThat(isLoop).isFalse();
    }

    @Test
    @DisplayName("未超阈值的重复调用不应触发")
    void shouldNotDetectBelowThreshold() {
        for (int i = 0; i < 3; i++) {
            boolean isLoop = detector.recordAndCheck("noteAction", "create", "same-param");
            assertThat(isLoop).isFalse();
        }
    }

    @Test
    @DisplayName("超过阈值的重复调用应触发")
    void shouldDetectLoopAboveThreshold() {
        for (int i = 0; i < 3; i++) {
            detector.recordAndCheck("noteAction", "create", "same-param");
        }
        boolean isLoop = detector.recordAndCheck("noteAction", "create", "same-param");
        assertThat(isLoop).isTrue();
    }

    @Test
    @DisplayName("不同签名的调用不应触发")
    void shouldNotDetectDifferentSignatures() {
        for (int i = 0; i < 10; i++) {
            boolean isLoop = detector.recordAndCheck("noteAction", "create", "param-" + i);
            assertThat(isLoop).isFalse();
        }
    }

    @Test
    @DisplayName("reset 后计数应清零")
    void shouldResetCounters() {
        for (int i = 0; i < 3; i++) {
            detector.recordAndCheck("noteAction", "create", "same-param");
        }
        detector.reset();

        boolean isLoop = detector.recordAndCheck("noteAction", "create", "same-param");
        assertThat(isLoop).isFalse();
    }
}
