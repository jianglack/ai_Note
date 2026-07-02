package com.ainote.app.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class PlanProgressEmitterTest {

    @Test
    void subscribeRegistersEmitter() {
        PlanProgressEmitter emitter = new PlanProgressEmitter();

        SseEmitter sseEmitter = emitter.subscribe("plan-1");

        assertThat(emitters(emitter)).containsKey("plan-1");
        assertThat(emitters(emitter).get("plan-1")).contains(sseEmitter);
    }

    @Test
    void emitsEventsInCallOrder() throws Exception {
        PlanProgressEmitter emitter = new PlanProgressEmitter();
        SseEmitter sseEmitter = mock(SseEmitter.class);
        emitters(emitter).put("plan-1", new CopyOnWriteArrayList<>(List.of(sseEmitter)));

        emitter.emitStepUpdate("plan-1", 1, "SUCCESS", "done");
        emitter.emitPlanUpdate("plan-1", "EXECUTING", 1, 2);
        emitter.emitLog("plan-1", "log message");

        verify(sseEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void removesEmitterWhenSendFails() throws Exception {
        PlanProgressEmitter emitter = new PlanProgressEmitter();
        SseEmitter sseEmitter = mock(SseEmitter.class);
        emitters(emitter).put("plan-1", new CopyOnWriteArrayList<>(List.of(sseEmitter)));
        doThrow(new IOException("closed")).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        emitter.emitLog("plan-1", "message");

        verify(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitters(emitter)).doesNotContainKey("plan-1");
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters(PlanProgressEmitter emitter) {
        return (ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>)
                ReflectionTestUtils.getField(emitter, "emitters");
    }
}
