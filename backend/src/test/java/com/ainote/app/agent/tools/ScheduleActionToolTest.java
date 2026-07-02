package com.ainote.app.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.model.ScheduleRequest;
import com.ainote.app.model.ScheduleResponse;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AiService;
import com.ainote.app.service.ScheduleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleActionToolTest {

    private ScheduleService scheduleService;
    private ScheduleRepository scheduleRepository;
    private SecurityUtils securityUtils;
    private ToolExecutionPipeline pipeline;
    private ScheduleActionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scheduleService = mock(ScheduleService.class);
        scheduleRepository = mock(ScheduleRepository.class);
        securityUtils = mock(SecurityUtils.class);
        pipeline = mock(ToolExecutionPipeline.class);
        tool = new ScheduleActionTool(scheduleService, scheduleRepository, securityUtils,
                mock(AiService.class), pipeline, new ObjectMapper());

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
                .thenAnswer(inv -> inv.<Supplier<String>>getArgument(4).get());
    }

    @Test
    void createPersistsWhenNoDuplicate() {
        when(scheduleRepository.findByUserIdAndTitleAndStartTime(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(scheduleService.createSchedule(any(ScheduleRequest.class), eq("user-1")))
                .thenReturn(mock(ScheduleResponse.class));

        String result = tool.scheduleAction("create", "{\"title\":\"开会\",\"startTime\":\"2024-03-15T10:00:00\"}");

        assertThat(result).contains("已创建日程").contains("开会");
        verify(scheduleService).createSchedule(argThat(request ->
                "开会".equals(request.getTitle())
                        && LocalDateTime.of(2024, 3, 15, 10, 0).equals(request.getStartTime())), eq("user-1"));
    }

    @Test
    void createRejectsMissingStartTime() {
        String result = tool.scheduleAction("create", "{\"title\":\"开会\"}");

        assertThat(result).contains("缺少 startTime");
        verify(scheduleService, never()).createSchedule(any(), anyString());
    }

    @Test
    void createRejectsUnparseableStartTime() {
        String result = tool.scheduleAction("create", "{\"title\":\"开会\",\"startTime\":\"tomorrow\"}");

        assertThat(result).contains("无法解析").contains("tomorrow");
        verify(scheduleService, never()).createSchedule(any(), anyString());
    }

    @Test
    void createIsIdempotentForSameTitleAndTime() {
        when(scheduleRepository.findByUserIdAndTitleAndStartTime(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(mock(com.ainote.app.entity.Schedule.class)));

        String result = tool.scheduleAction("create", "{\"title\":\"开会\",\"startTime\":\"2024-03-15T10:00:00\"}");

        assertThat(result).contains("已存在").contains("开会");
        verify(scheduleService, never()).createSchedule(any(), anyString());
    }

    @Test
    void deleteReturnsPendingAction() {
        String result = tool.scheduleAction("delete", "{\"scheduleId\":\"s1\"}");

        assertThat(result).contains("PENDING_ACTION").contains("DELETE_SCHEDULE");
        verify(scheduleService, never()).deleteSchedule(anyString(), anyString());
    }

    @Test
    void confirmDeleteInvokesService() {
        String result = tool.scheduleAction("confirmDelete", "{\"scheduleId\":\"s1\"}");

        assertThat(result).contains("日程已删除");
        verify(scheduleService).deleteSchedule("s1", "user-1");
    }

    @Test
    void listEmptyReturnsEmptyMessage() {
        when(scheduleService.getSchedules(eq("user-1"), any(), any())).thenReturn(List.of());

        String result = tool.scheduleAction("list", "{}");

        assertThat(result).contains("暂无日程");
    }

    @Test
    void unknownActionReturnsUnknownMessage() {
        assertThat(tool.scheduleAction("missing", "{}")).contains("未知操作");
    }
}
