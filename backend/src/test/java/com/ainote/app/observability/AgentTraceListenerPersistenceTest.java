package com.ainote.app.observability;

import com.ainote.app.agent.budget.TokenBudget;
import com.ainote.app.entity.AgentTrace;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.CostTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentTraceListenerPersistenceTest {

    @Test
    void enqueueTraceSaveDefersRepositoryWriteToExecutor() {
        AgentTraceRepository traceRepository = mock(AgentTraceRepository.class);
        CapturingExecutor traceExecutor = new CapturingExecutor();
        AgentTraceListener listener = new AgentTraceListener(
                traceRepository,
                mock(SecurityUtils.class),
                mock(CostTrackingService.class),
                mock(TokenBudget.class),
                new ObjectMapper(),
                traceExecutor,
                true,
                "deepseek-chat"
        );
        AgentTrace trace = new AgentTrace();
        trace.setId("trace-1");

        listener.enqueueTraceSave(trace, "saved {}", "trace-1");

        verify(traceRepository, never()).save(trace);
        traceExecutor.runCaptured();
        verify(traceRepository).save(trace);
    }

    private static final class CapturingExecutor implements Executor {
        private Runnable captured;

        @Override
        public void execute(Runnable command) {
            this.captured = command;
        }

        void runCaptured() {
            if (captured == null) {
                throw new AssertionError("No task captured");
            }
            captured.run();
        }
    }
}
