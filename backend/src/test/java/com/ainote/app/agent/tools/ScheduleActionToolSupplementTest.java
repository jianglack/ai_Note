package com.ainote.app.agent.tools;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.model.ExtractedSchedule;
import com.ainote.app.model.ScheduleRequest;
import com.ainote.app.model.ScheduleResponse;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AiService;
import com.ainote.app.service.ScheduleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleActionToolSupplementTest {

    private ScheduleService scheduleService;
    private ScheduleRepository scheduleRepository;
    private SecurityUtils securityUtils;
    private AiService aiService;
    private ScheduleActionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scheduleService = mock(ScheduleService.class);
        scheduleRepository = mock(ScheduleRepository.class);
        securityUtils = mock(SecurityUtils.class);
        aiService = mock(AiService.class);
        ToolExecutionPipeline pipeline = mock(ToolExecutionPipeline.class);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<String>>getArgument(4).get());

        tool = new ScheduleActionTool(
                scheduleService,
                scheduleRepository,
                securityUtils,
                aiService,
                pipeline,
                new ObjectMapper());
    }

    @Test
    void createHandlesTitleEndTimeRruleAndAlternateDateFormats() {
        when(scheduleRepository.findByUserIdAndTitleAndStartTime(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(scheduleService.createSchedule(any(ScheduleRequest.class), eq("user-1")))
                .thenReturn(mock(ScheduleResponse.class));

        assertThat(tool.scheduleAction("create", "{\"startTime\":\"2026-06-27T10:00:00\"}"))
                .contains("缺少 title");

        String result = tool.scheduleAction("create", """
                {"title":"晨会","startTime":"2026-06-27 10:00","endTime":"2026-06-27 11:00:00","allDay":true,"rrule":"FREQ=DAILY"}
                """);

        assertThat(result).contains("已创建日程").contains("晨会");
        verify(scheduleService).createSchedule(argThat(request ->
                "晨会".equals(request.getTitle())
                        && LocalDateTime.parse("2026-06-27T10:00:00").equals(request.getStartTime())
                        && LocalDateTime.parse("2026-06-27T11:00:00").equals(request.getEndTime())
                        && Boolean.TRUE.equals(request.getAllDay())
                        && "FREQ=DAILY".equals(request.getRrule())), eq("user-1"));
    }

    @Test
    void createBatchHandlesArraysFailuresAndLegacyDateTimeFields() {
        when(scheduleService.createSchedule(any(ScheduleRequest.class), eq("user-1")))
                .thenReturn(mock(ScheduleResponse.class))
                .thenThrow(new IllegalStateException("boom"));

        assertThat(tool.scheduleAction("createBatch", "{}")).contains("参数格式错误");

        String result = tool.scheduleAction("createBatch", """
                {"schedules":[
                  {"title":"A","date":"2026-06-27","time":"09:30"},
                  {"title":"","startTime":"2026-06-27T10:00:00"},
                  {"title":"Bad","startTime":"not-time"},
                  {"title":"B","startTime":"2026-06-27T11:00:00"}
                ]}
                """);

        assertThat(result)
                .contains("成功创建 1 个日程")
                .contains("创建失败 3 个")
                .contains("缺少 title")
                .contains("无法解析时间")
                .contains("boom");
        verify(scheduleService).createSchedule(argThat(request ->
                "A".equals(request.getTitle())
                        && LocalDateTime.parse("2026-06-27T09:30:00").equals(request.getStartTime())), eq("user-1"));
    }

    @Test
    void listFormatsRangesAndLimitsLongResultSets() {
        List<ScheduleResponse> schedules = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            ScheduleResponse response = mock(ScheduleResponse.class);
            when(response.getId()).thenReturn("schedule-" + i);
            when(response.getTitle()).thenReturn("日程 " + i);
            when(response.getStartTime()).thenReturn(LocalDateTime.parse("2026-06-27T0" + (i % 9 + 1) + ":00:00"));
            schedules.add(response);
        }
        when(scheduleService.getSchedules(eq("user-1"), any(), any())).thenReturn(schedules);

        String result = tool.scheduleAction("list", "{\"startDate\":\"2026-06-27\",\"endDate\":\"2026-06-28\"}");

        assertThat(result).contains("找到 11 个日程").contains("schedule-1").contains("还有 1");
    }

    @Test
    void extractFromNoteCoversMissingEmptyAndDetailedSchedules() {
        assertThat(tool.scheduleAction("extractFromNote", "{}")).contains("缺少 noteId");

        when(aiService.extractSchedules("note-empty"))
                .thenReturn(new ExtractedSchedule.ExtractResponse(List.of(), "note-empty"));
        assertThat(tool.scheduleAction("extractFromNote", "{\"noteId\":\"note-empty\"}"))
                .contains("未从笔记中识别");

        ExtractedSchedule extracted = new ExtractedSchedule(
                "发布会",
                "2026-06-27T10:00:00",
                "2026-06-27T11:00:00",
                true,
                "FREQ=WEEKLY",
                0.91,
                "note");
        when(aiService.extractSchedules("note-1"))
                .thenReturn(new ExtractedSchedule.ExtractResponse(List.of(extracted), "note-1"));

        String result = tool.scheduleAction("extractFromNote", "{\"noteId\":\"note-1\"}");

        assertThat(result)
                .contains("提取到 1 个日程")
                .contains("发布会")
                .contains("全天事件")
                .contains("FREQ=WEEKLY")
                .contains("91%");
    }

    @Test
    void invalidJsonFallsBackToEmptyParams() {
        String result = tool.scheduleAction("createBatch", "not-json");

        assertThat(result).contains("参数格式错误");
        verify(scheduleService, never()).createSchedule(any(), anyString());
    }
}
