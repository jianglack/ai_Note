package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionServiceTest {

    private final ReflectionService reflectionService = new ReflectionService(null);

    @Test
    void retriesWhenToolResponseReportsMissingParameter() {
        TaskStep step = new TaskStep();
        step.setStepOrder(7);
        step.setRetryCount(0);

        assertThat(reflectionService.reflect(step, "缺少 tagName", null))
                .isEqualTo(ReflectionService.Decision.RETRY);
    }

    @Test
    void retriesWhenToolResponseReportsInvalidScheduleTime() {
        TaskStep step = new TaskStep();
        step.setStepOrder(4);
        step.setRetryCount(0);

        assertThat(reflectionService.reflect(step, "无法解析开始时间「2026-06-04 19:00」", null))
                .isEqualTo(ReflectionService.Decision.RETRY);
    }
    @Test
    void retriesWhenChineseHardFailureIndicatorsAppear() {
        TaskStep step = new TaskStep();
        step.setStepOrder(5);
        step.setRetryCount(0);

        assertThat(reflectionService.reflect(step, "\u6267\u884c\u5931\u8d25\uff1a\u7b14\u8bb0\u4e0d\u5b58\u5728", null))
                .isEqualTo(ReflectionService.Decision.RETRY);
        assertThat(reflectionService.reflect(step, "\u65e0\u6743\u8bbf\u95ee\u8be5\u7b14\u8bb0", null))
                .isEqualTo(ReflectionService.Decision.RETRY);
    }

    @Test
    void reflectWithLlmFailureRetriesInsteadOfContinuing() {
        TaskStep step = new TaskStep();
        step.setStepOrder(6);

        assertThat(reflectionService.reflectWithLlm(step, "ambiguous", "expected"))
                .isEqualTo(ReflectionService.Decision.RETRY);
    }
}
