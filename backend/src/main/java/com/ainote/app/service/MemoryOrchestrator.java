package com.ainote.app.service;

import com.ainote.app.config.MemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class MemoryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemoryOrchestrator.class);

    private final MemoryProperties memoryProperties;
    private final MemoryCapturePolicy capturePolicy;
    private final MemoryCandidateExtractor candidateExtractor;
    private final MemoryWriteService writeService;

    public MemoryOrchestrator(MemoryProperties memoryProperties,
                              MemoryCapturePolicy capturePolicy,
                              MemoryCandidateExtractor candidateExtractor,
                              MemoryWriteService writeService) {
        this.memoryProperties = memoryProperties;
        this.capturePolicy = capturePolicy;
        this.candidateExtractor = candidateExtractor;
        this.writeService = writeService;
    }

    @Async("securityExecutor")
    public void captureAfterTurn(String userId, String userMessage, String aiResponse) {
        long startedAt = System.nanoTime();
        if (!memoryProperties.getCapture().isEnabled()) {
            logCapture("skipped", userId, "capture_disabled", 0, 0, startedAt);
            return;
        }
        if (memoryProperties.getCapture().getMode() != MemoryProperties.CaptureMode.POLICY) {
            logCapture("skipped", userId, "capture_mode_not_policy", 0, 0, startedAt);
            return;
        }

        try {
            MemoryCapturePolicy.CaptureRequest request =
                    new MemoryCapturePolicy.CaptureRequest(userId, userMessage, aiResponse);
            MemoryCapturePolicy.CaptureDecision decision = capturePolicy.evaluate(request);
            if (!decision.allowed()) {
                logCapture("denied", userId, decision.reason(), 0, 0, startedAt);
                return;
            }

            List<MemoryCandidateExtractor.MemoryCandidate> candidates = candidateExtractor.extract(request, decision);
            if (candidates.isEmpty()) {
                logCapture("skipped", userId, "no_candidates", 0, 0, startedAt);
                return;
            }

            MemoryWriteService.MemoryWriteResult result =
                    writeService.writeCandidates(userId, candidates, decision.reason());
            int writes = result.created() + result.reinforced() + result.superseded();
            logCapture("succeeded", userId, decision.reason(), candidates.size(), writes, startedAt);
        } catch (Exception e) {
            log.warn("memory_capture_event=failed user_id={} error={} message={}",
                    userId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private void logCapture(String status,
                            String userId,
                            String reason,
                            int candidateCount,
                            int writeCount,
                            long startedAt) {
        if (!memoryProperties.getAudit().isEnabled()) {
            return;
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        log.info("memory_capture_event={} user_id={} reason={} capture_mode={} candidate_count={} write_count={} duration_ms={}",
                status,
                userId,
                reason,
                memoryProperties.getCapture().getMode().name().toLowerCase(Locale.ROOT),
                candidateCount,
                writeCount,
                durationMs);
    }
}
