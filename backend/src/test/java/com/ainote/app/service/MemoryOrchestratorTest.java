package com.ainote.app.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ainote.app.config.MemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryOrchestratorTest {

    private MemoryProperties memoryProperties;
    private MemoryCapturePolicy policy;
    private MemoryCandidateExtractor extractor;
    private MemoryWriteService writeService;
    private MemoryOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        memoryProperties = new MemoryProperties();
        memoryProperties.getCapture().setMode(MemoryProperties.CaptureMode.POLICY);
        policy = new MemoryCapturePolicy();
        extractor = mock(MemoryCandidateExtractor.class);
        writeService = mock(MemoryWriteService.class);
        orchestrator = new MemoryOrchestrator(memoryProperties, policy, extractor, writeService);
    }

    @Test
    void deniedPolicyDoesNotExtractOrWrite() {
        orchestrator.captureAfterTurn("user-1", "删除全部笔记", "已删除");

        verify(extractor, never()).extract(any(), any());
        verify(writeService, never()).writeCandidates(eq("user-1"), any(), any());
    }

    @Test
    void explicitRememberWritesCandidates() {
        MemoryCandidateExtractor.MemoryCandidate candidate = new MemoryCandidateExtractor.MemoryCandidate(
                "preference",
                "preference",
                "prefers Chinese replies",
                0.95,
                "user",
                "记住，我希望你用中文回答",
                false);
        when(extractor.extract(any(), any())).thenReturn(List.of(candidate));
        when(writeService.writeCandidates(eq("user-1"), eq(List.of(candidate)), eq("explicit_memory")))
                .thenReturn(new MemoryWriteService.MemoryWriteResult(1, 0, 0, 0));

        orchestrator.captureAfterTurn("user-1", "记住，我希望你用中文回答", "好的");

        verify(writeService).writeCandidates("user-1", List.of(candidate), "explicit_memory");
    }

    @Test
    void extractionFailureIsLoggedAsObservableCaptureFailure() {
        when(extractor.extract(any(), any())).thenThrow(new IllegalStateException("extractor down"));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(MemoryOrchestrator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            orchestrator.captureAfterTurn("user-1", "记住，我希望你用中文回答", "好的");

            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("memory_capture_event=failed")
                            .contains("user_id=user-1")
                            .contains("IllegalStateException"));
            verify(writeService).recordCaptureFailure(
                    eq("user-1"),
                    eq("capture_exception"),
                    any(IllegalStateException.class),
                    any());
        } finally {
            logger.detachAppender(appender);
        }
    }
}
