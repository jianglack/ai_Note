package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private AgentTraceRepository traceRepository;
    private TaskPlanRepository planRepository;
    private TaskStepRepository stepRepository;
    private AgentMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        traceRepository = mock(AgentTraceRepository.class);
        planRepository = mock(TaskPlanRepository.class);
        stepRepository = mock(TaskStepRepository.class);
        when(traceRepository.aggregateToolMetrics(any())).thenReturn(List.of());
        metricsService = new AgentMetricsService(meterRegistry, traceRepository, planRepository, stepRepository);
    }

    @Test
    void recordsRealtimeMetricsAndFallsBackWhenDatabaseIsEmpty() {
        metricsService.recordPlanCreated();
        metricsService.recordPlanCompleted();
        metricsService.recordStepExecution(true);
        metricsService.recordStepRetry();
        metricsService.recordToolCall("noteAction", "create", true, 40);
        metricsService.recordToolCall("folderAction", "delete", false, 80);
        metricsService.recordToolRetry("folderAction");
        metricsService.recordCompensation(true, true);

        Map<String, Object> metrics = metricsService.getMetrics();

        assertThat(metrics)
                .containsEntry("plansTotal", 1L)
                .containsEntry("plansCompleted", 1L)
                .containsEntry("stepsTotal", 1L)
                .containsEntry("stepsSucceeded", 1L)
                .containsEntry("stepsRetried", 1L)
                .containsEntry("toolsCalls", 2L)
                .containsEntry("toolsCount", 2)
                .containsEntry("compensationTotal", 1L)
                .containsEntry("compensationVerified", 1L)
                .containsEntry("compensationRate", 100.0);

        assertThat(meterRegistry.counter("agent.tool.calls", "tool", "noteAction", "status", "success").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("agent.tool.retries", "tool", "folderAction").count())
                .isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) metrics.get("tools");
        assertThat(tools)
                .extracting(tool -> tool.get("name"))
                .containsExactlyInAnyOrder("noteAction", "folderAction");
    }
}
