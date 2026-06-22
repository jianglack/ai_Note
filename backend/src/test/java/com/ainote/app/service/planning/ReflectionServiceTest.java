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
}
