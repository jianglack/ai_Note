package com.ainote.app.service;

import com.ainote.app.entity.AiWorkflow;
import com.ainote.app.entity.AiWorkflowRun;
import com.ainote.app.repository.AiWorkflowRepository;
import com.ainote.app.repository.AiWorkflowRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 工作流执行服务 - 异步执行工作流步骤
 * 每个步骤是一个 Agent 对话，步骤之间可以传递上下文
 */
@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final AgentService agentService;
    private final AiWorkflowRepository workflowRepository;
    private final AiWorkflowRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public WorkflowExecutionService(AgentService agentService,
                                     AiWorkflowRepository workflowRepository,
                                     AiWorkflowRunRepository runRepository,
                                     ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.workflowRepository = workflowRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行工作流
     */
    @Async("taskExecutor")
    public void executeWorkflow(String workflowId, String runId, String userId) {
        log.info("=== 开始执行工作流 === workflowId={}, runId={}", workflowId, runId);

        AiWorkflowRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;

        run.setStatus("running");
        runRepository.save(run);

        try {
            AiWorkflow workflow = workflowRepository.findById(workflowId).orElseThrow();
            List<Map<String, String>> steps = parseSteps(workflow.getSteps());

            List<Map<String, Object>> results = new ArrayList<>();
            String previousOutput = "";

            for (int i = 0; i < steps.size(); i++) {
                Map<String, String> step = steps.get(i);
                String instruction = step.getOrDefault("instruction", "");
                String type = step.getOrDefault("type", "ai_chat");

                // 将上一步的输出作为上下文传递
                String query = instruction;
                if (!previousOutput.isEmpty()) {
                    query = "基于上一步的结果：\n" + previousOutput + "\n\n现在执行：" + instruction;
                }

                log.info("  执行步骤 {}/{}: type={}, instruction={}", i + 1, steps.size(), type, instruction.substring(0, Math.min(50, instruction.length())));

                Map<String, Object> stepResult = new LinkedHashMap<>();
                stepResult.put("step", i + 1);
                stepResult.put("instruction", instruction);

                try {
                    if ("ai_chat".equals(type) || "summarize".equals(type) || "analyze".equals(type)) {
                        var response = agentService.chat(query, List.of(), userId);
                        previousOutput = response.getContent();
                        stepResult.put("status", "completed");
                        stepResult.put("output", previousOutput);
                    } else {
                        // 未知步骤类型，当作 AI 对话处理
                        var response = agentService.chat(query, List.of(), userId);
                        previousOutput = response.getContent();
                        stepResult.put("status", "completed");
                        stepResult.put("output", previousOutput);
                    }
                } catch (Exception e) {
                    log.error("  步骤 {} 执行失败: {}", i + 1, e.getMessage());
                    stepResult.put("status", "failed");
                    stepResult.put("error", e.getMessage());
                    previousOutput = "";
                }

                results.add(stepResult);
            }

            // 保存结果
            run.setStatus("completed");
            run.setResults(objectMapper.writeValueAsString(results));
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);

            // 更新工作流最后运行时间
            AiWorkflow wf = workflowRepository.findById(workflowId).orElse(null);
            if (wf != null) {
                wf.setLastRunAt(LocalDateTime.now());
                workflowRepository.save(wf);
            }

            log.info("=== 工作流执行完成 === runId={}, steps={}", runId, results.size());

        } catch (Exception e) {
            log.error("工作流执行异常: runId={}, error={}", runId, e.getMessage(), e);
            run.setStatus("failed");
            run.setError(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
        }
    }

    private List<Map<String, String>> parseSteps(String stepsJson) {
        try {
            List<Map<String, String>> steps = objectMapper.readValue(stepsJson, new TypeReference<>() {});
            if (steps.isEmpty()) {
                // 如果步骤为空，创建一个默认步骤
                steps = List.of(Map.of("type", "ai_chat", "instruction", "总结我最近的笔记内容"));
            }
            return steps;
        } catch (Exception e) {
            return List.of(Map.of("type", "ai_chat", "instruction", "总结我最近的笔记内容"));
        }
    }
}
