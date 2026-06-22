package com.ainote.ai.agent.tools;

import com.ainote.ai.agent.pipeline.ToolExecutionPipeline;
import com.ainote.ai.feign.ScheduleClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 日程复合工具（微服务版）
 * 通过 Feign 客户端调用 schedule-service，所有调用经 ToolExecutionPipeline 管道
 */
@Component
public class ScheduleActionTool {

    private static final Logger log = LoggerFactory.getLogger(ScheduleActionTool.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ScheduleClient scheduleClient;
    private final ObjectMapper objectMapper;
    private final ToolExecutionPipeline pipeline;

    public ScheduleActionTool(ScheduleClient scheduleClient, ToolExecutionPipeline pipeline) {
        this.scheduleClient = scheduleClient;
        this.objectMapper = new ObjectMapper();
        this.pipeline = pipeline;
    }

    @Tool("""
        日程操作复合工具。
        action 取值及参数：
        - create: {"title":"标题","startTime":"yyyy-MM-ddTHH:mm:ss"} 可选: allDay(boolean), rrule(iCalendar格式)
        - createBatch: {"schedules":[{"title":"标题","startTime":"..."},...]} 批量创建多个日程
        - list: {} 列出全部日程；可选 {"startDate":"...","endDate":"..."} 按时间范围筛选
        - delete: {"scheduleId":"ID"} → 返回确认请求，需用户确认后调用 confirmDelete
        - confirmDelete: {"scheduleId":"ID"} 用户确认后执行删除
        注意：startTime 必须为 ISO 8601 格式。
        """)
    public String scheduleAction(
            @P("操作类型") String action,
            @P("参数 JSON，如 {\"title\":\"开会\",\"startTime\":\"2024-03-15T10:00:00\"}") String paramsJson
    ) {
        log.info("Tool: scheduleAction called with action='{}', params='{}'", action, paramsJson);
        try {
            JsonNode params = parseParams(paramsJson);
            return pipeline.execute("system", "scheduleAction", action, params,
                    () -> doExecute(action, params));
        } catch (Exception e) {
            log.error("Tool: scheduleAction failed for action={}", action, e);
            return "日程操作失败：" + e.getMessage();
        }
    }

    private String doExecute(String action, JsonNode params) {
        return switch (action) {
            case "create" -> doCreate(params);
            case "createBatch" -> doCreateBatch(params);
            case "list" -> doList(params);
            case "delete" -> doDelete(params);
            case "confirmDelete" -> doConfirmDelete(params);
            default -> "未知操作: " + action;
        };
    }

    private JsonNode parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String s = paramsJson.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            log.warn("Failed to parse paramsJson='{}', using empty object.", paramsJson);
            return objectMapper.createObjectNode();
        }
    }

    private String doCreate(JsonNode params) {
        String title = params.path("title").asText("");
        String startTime = params.path("startTime").asText("");
        boolean allDay = params.path("allDay").asBoolean(false);
        String rrule = params.path("rrule").asText(null);

        if (title.isEmpty()) return "缺少 title";
        if (startTime.isEmpty()) return "缺少 startTime";

        LocalDateTime parsedStartTime = parseDateTime(startTime);
        if (parsedStartTime == null) {
            return "无法解析开始时间「" + startTime + "」，请使用 ISO 8601 格式";
        }

        Map<String, Object> request = new HashMap<>();
        request.put("title", title);
        request.put("startTime", startTime);
        request.put("allDay", allDay);
        if (rrule != null && !rrule.isEmpty()) {
            request.put("rrule", rrule);
        }

        Map<String, Object> response = scheduleClient.createSchedule(request);
        if (response.containsKey("error")) return "创建日程失败：" + response.get("error");

        String formattedTime = parsedStartTime.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
        return String.format("已创建日程「%s」，时间：%s。", title, formattedTime);
    }

    private String doCreateBatch(JsonNode params) {
        JsonNode schedulesArray = params.path("schedules");
        if (!schedulesArray.isArray()) {
            if (params.isArray()) {
                schedulesArray = params;
            } else {
                return "参数格式错误，需要 schedules 数组";
            }
        }

        List<String> created = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (JsonNode scheduleNode : schedulesArray) {
            String title = scheduleNode.path("title").asText("");
            String startTime = scheduleNode.path("startTime").asText("");
            boolean allDay = scheduleNode.path("allDay").asBoolean(false);
            String rrule = scheduleNode.path("rrule").asText(null);

            if (title.isEmpty() || startTime.isEmpty()) {
                failed.add("缺少 title 或 startTime");
                continue;
            }

            try {
                Map<String, Object> request = new HashMap<>();
                request.put("title", title);
                request.put("startTime", startTime);
                request.put("allDay", allDay);
                if (rrule != null && !rrule.isEmpty()) {
                    request.put("rrule", rrule);
                }
                scheduleClient.createSchedule(request);
                created.add(title);
            } catch (Exception e) {
                failed.add(title + ": " + e.getMessage());
            }
        }

        StringBuilder result = new StringBuilder();
        if (!created.isEmpty()) {
            result.append("成功创建 ").append(created.size()).append(" 个日程：\n");
            for (int i = 0; i < created.size(); i++) {
                result.append((i + 1)).append(". ").append(created.get(i)).append("\n");
            }
        }
        if (!failed.isEmpty()) {
            result.append("创建失败 ").append(failed.size()).append(" 个：\n");
            for (String f : failed) {
                result.append("- ").append(f).append("\n");
            }
        }
        return result.toString().trim();
    }

    private String doList(JsonNode params) {
        List<Map<String, Object>> schedules = scheduleClient.listSchedules();
        if (schedules.isEmpty()) return "暂无日程";

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(schedules.size()).append(" 个日程：\n");
        for (int i = 0; i < Math.min(schedules.size(), 10); i++) {
            Map<String, Object> s = schedules.get(i);
            sb.append(String.format("%d. 「%s」- %s (ID: %s)\n",
                    i + 1, s.get("title"), s.get("startTime"), s.get("id")));
        }
        if (schedules.size() > 10) {
            sb.append("... 还有 ").append(schedules.size() - 10).append(" 个日程");
        }
        return sb.toString();
    }

    private String doDelete(JsonNode params) {
        String scheduleId = params.path("scheduleId").asText("");
        if (scheduleId.isEmpty()) return "缺少 scheduleId";
        return String.format("PENDING_ACTION:{\"type\":\"DELETE_SCHEDULE\",\"scheduleId\":\"%s\"}\n确认要删除该日程吗？", scheduleId);
    }

    private String doConfirmDelete(JsonNode params) {
        String scheduleId = params.path("scheduleId").asText("");
        if (scheduleId.isEmpty()) return "缺少 scheduleId";
        scheduleClient.deleteSchedule(Long.parseLong(scheduleId));
        return "日程已删除";
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) return null;
        try { return LocalDateTime.parse(dateTimeStr, ISO_FORMATTER); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr + "T00:00:00", ISO_FORMATTER); } catch (DateTimeParseException ignored) {}
        return null;
    }
}
