package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.ContextAssembler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (E)");

    private final ChatModel chatModel;
    private final ContextAssembler contextAssembler;
    private final TaskPlanRepository planRepository;
    private final TaskStepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    @Value("${app.planning.max-steps:20}")
    private int maxSteps;

    public PlannerService(
            @Qualifier("agentChatModel") ChatModel chatModel,
            ContextAssembler contextAssembler,
            TaskPlanRepository planRepository,
            TaskStepRepository stepRepository,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.contextAssembler = contextAssembler;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPrompt();
    }

    private String loadPrompt() {
        try {
            var resource = new ClassPathResource("prompts/planner-system.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load planner prompt", e);
            return "You are a task planner. Decompose the user request into steps. Output JSON only.";
        }
    }

    public TaskPlan generatePlan(String query, List<String> noteIds, String userId) {
        String noteContext = contextAssembler.assemble(query, noteIds, userId);
        String timeContext = LocalDateTime.now().format(TIME_FMT);

        String systemPrompt = promptTemplate
                .replace("{{currentTime}}", timeContext)
                .replace("{{noteContext}}", noteContext != null ? noteContext : "");

        var chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(query))
                .build();
        var response = chatModel.chat(chatRequest);
        String planJson = response.aiMessage().text().trim();

        if (planJson.startsWith("```")) {
            planJson = planJson.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        log.info("Planner LLM output: {}", planJson);
        return parsePlanAndPersist(query, planJson, userId);
    }

    private TaskPlan parsePlanAndPersist(String query, String planJson, String userId) {
        try {
            JsonNode root = objectMapper.readTree(planJson);

            TaskPlan plan = new TaskPlan();
            plan.setUserId(userId);
            plan.setOriginalQuery(query);
            plan.setGoal(root.has("goal") ? root.get("goal").asText() : query);
            plan.setPlanJson(planJson);
            plan.setStatus(TaskPlan.STATUS_AWAITING_APPROVAL);

            JsonNode steps = root.get("steps");
            int stepCount = (steps != null && steps.isArray()) ? Math.min(steps.size(), maxSteps) : 0;
            plan.setTotalSteps(stepCount);
            plan = planRepository.save(plan);

            if (steps != null && steps.isArray()) {
                for (int i = 0; i < stepCount; i++) {
                    JsonNode s = steps.get(i);
                    TaskStep step = new TaskStep();
                    step.setPlanId(plan.getId());
                    step.setStepOrder(s.has("order") ? s.get("order").asInt() : i + 1);
                    step.setAction(s.has("action") ? s.get("action").asText() : "unknown");
                    step.setDescription(s.has("description") ? s.get("description").asText() : "");
                    if (s.has("params")) {
                        step.setInputParams(objectMapper.writeValueAsString(s.get("params")));
                    }
                    if (s.has("dependsOn") && s.get("dependsOn").isArray()) {
                        List<Integer> deps = new ArrayList<>();
                        for (JsonNode d : s.get("dependsOn")) deps.add(d.asInt());
                        step.setDependsOn(deps.toArray(new Integer[0]));
                    }
                    stepRepository.save(step);
                }
            }

            log.info("Plan created: id={}, goal={}, steps={}", plan.getId(), plan.getGoal(), stepCount);
            return plan;

        } catch (Exception e) {
            log.error("Failed to parse plan JSON: {}", e.getMessage());
            TaskPlan plan = new TaskPlan();
            plan.setUserId(userId);
            plan.setOriginalQuery(query);
            plan.setGoal(query);
            plan.setPlanJson(planJson);
            plan.setStatus(TaskPlan.STATUS_FAILED);
            return planRepository.save(plan);
        }
    }

    public void replanRemaining(TaskPlan plan, TaskStep failedStep, String failureContext, String userId) {
        String query = String.format(
                "原始任务: %s\n已完成步骤: %d/%d\n当前步骤「%s」结果: %s\n请重新规划剩余步骤。",
                plan.getOriginalQuery(), plan.getCompletedSteps(), plan.getTotalSteps(),
                failedStep.getDescription(), failureContext);

        String noteContext = contextAssembler.assemble(query, List.of(), userId);
        String timeContext = LocalDateTime.now().format(TIME_FMT);
        String systemPrompt = promptTemplate
                .replace("{{currentTime}}", timeContext)
                .replace("{{noteContext}}", noteContext != null ? noteContext : "");

        var replanRequest = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(query))
                .build();
        var replanResponse = chatModel.chat(replanRequest);
        String newPlanJson = replanResponse.aiMessage().text().trim();
        if (newPlanJson.startsWith("```")) {
            newPlanJson = newPlanJson.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        try {
            JsonNode root = objectMapper.readTree(newPlanJson);
            JsonNode steps = root.get("steps");
            if (steps == null || !steps.isArray()) return;

            var pendingSteps = stepRepository.findByPlanIdAndStatusOrderByStepOrder(plan.getId(), TaskStep.STATUS_PENDING);
            stepRepository.deleteAll(pendingSteps);

            int baseOrder = failedStep.getStepOrder();
            int newCount = Math.min(steps.size(), maxSteps - plan.getCompletedSteps());
            for (int i = 0; i < newCount; i++) {
                JsonNode s = steps.get(i);
                TaskStep step = new TaskStep();
                step.setPlanId(plan.getId());
                step.setStepOrder(baseOrder + i + 1);
                step.setAction(s.has("action") ? s.get("action").asText() : "unknown");
                step.setDescription(s.has("description") ? s.get("description").asText() : "");
                if (s.has("params")) step.setInputParams(objectMapper.writeValueAsString(s.get("params")));
                stepRepository.save(step);
            }

            plan.setTotalSteps(plan.getCompletedSteps() + newCount);
            plan.setPlanJson(newPlanJson);
            planRepository.save(plan);

            log.info("Replanned: planId={}, new remaining steps={}", plan.getId(), newCount);
        } catch (Exception e) {
            log.error("Replan failed: {}", e.getMessage());
        }
    }
}
