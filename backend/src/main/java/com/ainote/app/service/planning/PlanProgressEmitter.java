package com.ainote.app.service.planning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 进度广播器 — 管理计划执行的实时进度推送
 */
@Component
public class PlanProgressEmitter {

    private static final Logger log = LoggerFactory.getLogger(PlanProgressEmitter.class);

    // planId → List<SseEmitter>
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 注册一个 SSE 连接监听某个计划的进度
     */
    public SseEmitter subscribe(String planId) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        emitters.computeIfAbsent(planId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(planId, emitter));
        emitter.onTimeout(() -> removeEmitter(planId, emitter));
        emitter.onError(e -> removeEmitter(planId, emitter));

        return emitter;
    }

    /**
     * 发送步骤状态变更事件
     */
    public void emitStepUpdate(String planId, int stepOrder, String status, String description) {
        Map<String, Object> data = Map.of(
                "type", "step_update",
                "planId", planId,
                "stepOrder", stepOrder,
                "status", status,
                "description", description,
                "timestamp", System.currentTimeMillis()
        );
        broadcast(planId, "step_update", data);
    }

    /**
     * 发送计划状态变更事件
     */
    public void emitPlanUpdate(String planId, String status, int completedSteps, int totalSteps) {
        Map<String, Object> data = Map.of(
                "type", "plan_update",
                "planId", planId,
                "status", status,
                "completedSteps", completedSteps,
                "totalSteps", totalSteps,
                "timestamp", System.currentTimeMillis()
        );
        broadcast(planId, "plan_update", data);
    }

    /**
     * 发送执行日志事件
     */
    public void emitLog(String planId, String message) {
        Map<String, Object> data = Map.of(
                "type", "log",
                "planId", planId,
                "message", message,
                "timestamp", System.currentTimeMillis()
        );
        broadcast(planId, "log", data);
    }

    private void broadcast(String planId, String eventName, Object data) {
        List<SseEmitter> planEmitters = emitters.get(planId);
        if (planEmitters == null || planEmitters.isEmpty()) return;

        for (SseEmitter emitter : planEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                removeEmitter(planId, emitter);
            }
        }
    }

    private void removeEmitter(String planId, SseEmitter emitter) {
        List<SseEmitter> planEmitters = emitters.get(planId);
        if (planEmitters != null) {
            planEmitters.remove(emitter);
            if (planEmitters.isEmpty()) {
                emitters.remove(planId);
            }
        }
    }
}
