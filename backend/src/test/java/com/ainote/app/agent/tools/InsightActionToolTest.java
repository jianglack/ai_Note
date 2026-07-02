package com.ainote.app.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.NoteInsightService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InsightActionToolTest {

    private NoteInsightService insightService;
    private SecurityUtils securityUtils;
    private ToolExecutionPipeline pipeline;
    private InsightActionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        insightService = mock(NoteInsightService.class);
        securityUtils = mock(SecurityUtils.class);
        pipeline = mock(ToolExecutionPipeline.class);
        tool = new InsightActionTool(insightService, securityUtils, pipeline, new ObjectMapper());

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(pipeline.execute(anyString(), anyString(), anyString(), any(JsonNode.class), any(Supplier.class)))
                .thenAnswer(inv -> inv.<Supplier<String>>getArgument(4).get());
    }

    @Test
    void statisticsReturnsMapAsJson() {
        when(insightService.getStatistics("user-1"))
                .thenReturn(Map.of("totalNotes", 3, "activeDays", 2));

        String result = tool.insightAction("statistics", "{}");

        assertThat(result).contains("\"totalNotes\":3").contains("\"activeDays\":2");
    }

    @Test
    void analyzeReturnsServiceText() {
        when(insightService.analyzeInsights("user-1")).thenReturn("主题聚类完成");

        String result = tool.insightAction("analyze", "{}");

        assertThat(result).contains("主题聚类完成");
    }

    @Test
    void duplicatesEmptyReturnsEmptyMessage() {
        when(insightService.findDuplicateCandidates("user-1")).thenReturn(List.of());

        String result = tool.insightAction("duplicates", "{}");

        assertThat(result).contains("没有发现");
    }

    @Test
    void duplicatesReturnsCandidates() {
        when(insightService.findDuplicateCandidates("user-1"))
                .thenReturn(List.of(Map.of("noteId", "n1", "score", 0.92)));

        String result = tool.insightAction("duplicates", "{}");

        assertThat(result).contains("\"noteId\":\"n1\"").contains("\"score\":0.92");
    }

    @Test
    void timelineUsesDefaultSevenDays() {
        when(insightService.getActivityTimeline("user-1", 7)).thenReturn(List.of());

        String result = tool.insightAction("timeline", "{}");

        assertThat(result).contains("最近 7 天没有活动记录");
        verify(insightService).getActivityTimeline("user-1", 7);
    }

    @Test
    void timelineUsesCustomDays() {
        when(insightService.getActivityTimeline("user-1", 14))
                .thenReturn(List.of(Map.of("noteId", "n1", "action", "updated")));

        String result = tool.insightAction("timeline", "{\"days\":14}");

        assertThat(result).contains("\"noteId\":\"n1\"").contains("\"action\":\"updated\"");
        verify(insightService).getActivityTimeline("user-1", 14);
    }

    @Test
    void unknownActionReturnsUnknownMessage() {
        assertThat(tool.insightAction("missing", "{}")).contains("未知的洞察操作");
    }
}
