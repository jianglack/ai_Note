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
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 日程复合工具
 * 将 7 个细粒度日程工具压缩为 1 个复合工具
 */
@Component
public class ScheduleActionTool {

    private static final Logger log = LoggerFactory.getLogger(ScheduleActionTool.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter SPACE_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SPACE_SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ScheduleService scheduleService;
    private final ScheduleRepository scheduleRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private final AiService aiService;
    private final ToolExecutionPipeline pipeline;

    public ScheduleActionTool(ScheduleService scheduleService, ScheduleRepository scheduleRepository,
                              SecurityUtils securityUtils, @Lazy AiService aiService,
                              ToolExecutionPipeline pipeline) {
        this.scheduleService = scheduleService;
        this.scheduleRepository = scheduleRepository;
        this.securityUtils = securityUtils;
        this.objectMapper = new ObjectMapper();
        this.aiService = aiService;
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
        - extractFromNote: {"noteId":"ID"} 从笔记内容中智能提取日程
        注意：startTime 必须为 ISO 8601 格式；create 有幂等保护，同 title+startTime 不会重复创建。
        """)
    public String scheduleAction(
            @P("操作类型") String action,
            @P("参数 JSON，如 {\"title\":\"开会\",\"startTime\":\"2024-03-15T10:00:00\"}") String paramsJson
    ) {
        log.info("Tool: scheduleAction called with action='{}', params='{}'", action, paramsJson);
        try {
            JsonNode params = parseParams(paramsJson);
            String userId = securityUtils.getCurrentUserId();

            return pipeline.execute(userId, "scheduleAction", action, params,
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
            case "extractFromNote" -> doExtractFromNote(params);
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
            log.warn("Failed to parse paramsJson='{}', using empty object. Error: {}", paramsJson, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String doCreate(JsonNode params) {
        String title = params.path("title").asText("");
        String startTime = params.path("startTime").asText("");
        String endTime = params.path("endTime").asText("");
        String description = params.path("description").asText(null);
        boolean allDay = params.path("allDay").asBoolean(false);
        String rrule = params.path("rrule").asText(null);

        if (title.isEmpty()) return "缺少 title";
        if (startTime.isEmpty()) return "缺少 startTime";

        String userId = securityUtils.getCurrentUserId();
        LocalDateTime parsedStartTime = parseDateTime(startTime);
        if (parsedStartTime == null) {
            return "无法解析开始时间「" + startTime + "」，请使用 ISO 8601 格式（如 2024-03-15T10:00:00）";
        }

        // 幂等保护
        List<com.ainote.app.entity.Schedule> duplicates = scheduleRepository.findByUserIdAndTitleAndStartTime(userId, title, parsedStartTime);
        if (!duplicates.isEmpty()) {
            String formattedTime = parsedStartTime.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
            return String.format("该日程已存在：「%s」，时间：%s，无需重复创建。", title, formattedTime);
        }

        ScheduleRequest request = new ScheduleRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setStartTime(parsedStartTime);
        request.setEndTime(parseDateTime(endTime));
        request.setAllDay(allDay);
        if (rrule != null && !rrule.isEmpty()) {
            request.setRrule(rrule);
        }

        ScheduleResponse response = scheduleService.createSchedule(request, userId);
        String formattedTime = parsedStartTime.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
        return String.format("已创建日程「%s」，时间：%s。", title, formattedTime);
    }

    private String doCreateBatch(JsonNode params) {
        JsonNode schedulesArray = params.path("schedules");
        if (!schedulesArray.isArray()) {
            schedulesArray = params.path("events");
        }
        if (!schedulesArray.isArray()) {
            // 如果 params 本身就是数组，尝试直接使用
            if (params.isArray()) {
                schedulesArray = params;
            } else {
                return "参数格式错误，需要 schedules 数组";
            }
        }

        String userId = securityUtils.getCurrentUserId();
        List<String> created = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (JsonNode scheduleNode : schedulesArray) {
            String title = scheduleNode.path("title").asText("");
            String startTime = scheduleNode.path("startTime").asText("");
            if (startTime.isEmpty() && scheduleNode.has("date") && scheduleNode.has("time")) {
                startTime = scheduleNode.path("date").asText("") + " " + scheduleNode.path("time").asText("");
            }
            String endTime = scheduleNode.path("endTime").asText("");
            String description = scheduleNode.path("description").asText(null);
            boolean allDay = scheduleNode.path("allDay").asBoolean(false);
            String rrule = scheduleNode.path("rrule").asText(null);

            if (title.isEmpty() || startTime.isEmpty()) {
                failed.add("缺少 title 或 startTime");
                continue;
            }

            LocalDateTime parsedStartTime = parseDateTime(startTime);
            if (parsedStartTime == null) {
                failed.add("无法解析时间：" + startTime);
                continue;
            }

            try {
                ScheduleRequest request = new ScheduleRequest();
                request.setTitle(title);
                request.setDescription(description);
                request.setStartTime(parsedStartTime);
                request.setEndTime(parseDateTime(endTime));
                request.setAllDay(allDay);
                if (rrule != null && !rrule.isEmpty()) {
                    request.setRrule(rrule);
                }
                scheduleService.createSchedule(request, userId);
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
        String userId = securityUtils.getCurrentUserId();
        String startDate = params.path("startDate").asText(null);
        String endDate = params.path("endDate").asText(null);

        LocalDateTime start = parseDateTime(startDate);
        LocalDateTime end = parseDateTime(endDate);

        List<ScheduleResponse> schedules = scheduleService.getSchedules(userId, start, end);

        if (schedules.isEmpty()) return "暂无日程";

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(schedules.size()).append(" 个日程：\n");
        for (int i = 0; i < Math.min(schedules.size(), 10); i++) {
            ScheduleResponse s = schedules.get(i);
            String time = s.getStartTime().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
            sb.append(String.format("%d. 「%s」- %s (ID: %s)\n", i + 1, s.getTitle(), time, s.getId()));
        }
        if (schedules.size() > 10) {
            sb.append("... 还有 ").append(schedules.size() - 10).append(" 个日程");
        }
        return sb.toString();
    }

    private String doDelete(JsonNode params) {
        String scheduleId = params.path("scheduleId").asText("");
        if (scheduleId.isEmpty()) return "缺少 scheduleId";
        return String.format("PENDING_ACTION:{\"type\":\"DELETE_SCHEDULE\",\"scheduleId\":\"%s\"}\n⚠️ 确认要删除该日程吗？", scheduleId);
    }

    private String doConfirmDelete(JsonNode params) {
        String scheduleId = params.path("scheduleId").asText("");
        if (scheduleId.isEmpty()) return "缺少 scheduleId";

        String userId = securityUtils.getCurrentUserId();
        scheduleService.deleteSchedule(scheduleId, userId);
        return "✅ 日程已删除";
    }

    private String doExtractFromNote(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        ExtractedSchedule.ExtractResponse response = aiService.extractSchedules(noteId);

        if (response.getSchedules() == null || response.getSchedules().isEmpty()) {
            return "未从笔记中识别出日程信息";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📅 从笔记中提取到 ").append(response.getSchedules().size()).append(" 个日程：\n\n");

        for (int i = 0; i < response.getSchedules().size(); i++) {
            ExtractedSchedule s = response.getSchedules().get(i);
            sb.append(String.format("%d. 「%s」\n", i + 1, s.getTitle()));
            sb.append("   时间：").append(s.getStartTime());
            if (s.getEndTime() != null && !s.getEndTime().isEmpty()) {
                sb.append(" ~ ").append(s.getEndTime());
            }
            sb.append("\n");
            if (s.getAllDay() != null && s.getAllDay()) {
                sb.append("   全天事件\n");
            }
            if (s.getRrule() != null && !s.getRrule().isEmpty()) {
                sb.append("   重复规则：").append(s.getRrule()).append("\n");
            }
            if (s.getConfidence() != null) {
                sb.append("   置信度：").append(String.format("%.0f%%", s.getConfidence() * 100)).append("\n");
            }
        }

        sb.append("\n是否需要我将这些日程添加到你的日程表？");
        return sb.toString();
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) return null;

        try { return LocalDateTime.parse(dateTimeStr, ISO_FORMATTER); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr, SPACE_SECOND_FORMATTER); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr, SPACE_MINUTE_FORMATTER); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(dateTimeStr + "T00:00:00", ISO_FORMATTER); } catch (DateTimeParseException ignored) {}

        log.warn("Failed to parse datetime: {}", dateTimeStr);
        return null;
    }
}
