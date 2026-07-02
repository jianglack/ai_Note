package com.ainote.app.controller;

import com.ainote.app.model.PlanSmartChatRequest;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.PlanningAgentService;
import com.ainote.app.service.planning.LlmTaskRouterService;
import com.ainote.app.service.planning.PlanProgressEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanControllerBackpressureTest {

    @Test
    void smartChatRejectsNinthConcurrentRequestWithServiceUnavailable() throws Exception {
        PlanningAgentService planningService = mock(PlanningAgentService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        PlanController controller = controller(planningService, securityUtils);
        PlanSmartChatRequest request = request();

        CountDownLatch entered = new CountDownLatch(8);
        CountDownLatch release = new CountDownLatch(1);
        when(planningService.smartChat(anyString(), anyList(), eq("user-1"), eq(false)))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    return Map.of("type", "direct", "content", "ok");
                });

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<ResponseEntity<?>>> inFlight = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                inFlight.add(executor.submit(() -> controller.smartChat(request)));
            }

            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            ResponseEntity<?> rejected = controller.smartChat(request);

            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(rejected.getBody()).isEqualTo(Map.of("error", "AGENT_BUSY", "message", "AI agent is busy"));

            release.countDown();
            for (Future<ResponseEntity<?>> future : inFlight) {
                assertThat(future.get(5, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void smartChatReleasesPermitWhenServiceThrows() {
        PlanningAgentService planningService = mock(PlanningAgentService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        PlanController controller = controller(planningService, securityUtils);
        PlanSmartChatRequest request = request();

        when(planningService.smartChat(anyString(), anyList(), eq("user-1"), eq(false)))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(Map.of("type", "direct", "content", "ok"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.smartChat(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");

        ResponseEntity<?> response = controller.smartChat(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("type", "direct", "content", "ok"));
    }

    private static PlanController controller(PlanningAgentService planningService, SecurityUtils securityUtils) {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        return new PlanController(
                planningService,
                mock(PlanProgressEmitter.class),
                securityUtils,
                mock(LlmTaskRouterService.class));
    }

    private static PlanSmartChatRequest request() {
        PlanSmartChatRequest request = new PlanSmartChatRequest();
        request.setQuery("make a plan");
        request.setNoteIds(List.of());
        request.setForcePlan(false);
        return request;
    }
}
