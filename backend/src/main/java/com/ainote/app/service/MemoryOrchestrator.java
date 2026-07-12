package com.ainote.app.service;

import com.ainote.app.config.MemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class MemoryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemoryOrchestrator.class);

    private final MemoryProperties memoryProperties;
    private final MemoryCapturePolicy capturePolicy;
    private final MemoryCandidateExtractor candidateExtractor;
    private final MemoryWriteService writeService;
    private final MemoryMetricsService memoryMetricsService;

    public MemoryOrchestrator(MemoryProperties memoryProperties,
                              MemoryCapturePolicy capturePolicy,
                              MemoryCandidateExtractor candidateExtractor,
                              MemoryWriteService writeService) {
        this(memoryProperties, capturePolicy, candidateExtractor, writeService, MemoryMetricsService.noop());
    }

    @Autowired
    public MemoryOrchestrator(MemoryProperties memoryProperties,
                              MemoryCapturePolicy capturePolicy,
                              MemoryCandidateExtractor candidateExtractor,
                              MemoryWriteService writeService,
                              MemoryMetricsService memoryMetricsService) {
        this.memoryProperties = memoryProperties;
        this.capturePolicy = capturePolicy;
        this.candidateExtractor = candidateExtractor;
        this.writeService = writeService;
        this.memoryMetricsService = memoryMetricsService == null ? MemoryMetricsService.noop() : memoryMetricsService;
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
                recordCaptureDecision(userId, "denied", decision.reason(), decision, userMessage);
                logCapture("denied", userId, decision.reason(), 0, 0, startedAt);
                return;
            }

            List<MemoryCandidateExtractor.MemoryCandidate> candidates = candidateExtractor.extract(request, decision);
            if (candidates.isEmpty()) {
                recordCaptureDecision(userId, "skipped", "no_candidates", decision, userMessage);
                logCapture("skipped", userId, "no_candidates", 0, 0, startedAt);
                return;
            }

            MemoryWriteService.MemoryWriteResult result =
                    writeService.writeCandidates(userId, candidates, decision.reason());
            int writes = result.created() + result.reinforced() + result.superseded();
            logCapture("succeeded", userId, decision.reason(), candidates.size(), writes, startedAt);
        } catch (Exception e) {
            memoryMetricsService.recordCapture("failed", "capture_exception", 0, 0, elapsedMs(startedAt));
            log.warn("memory_capture_event=failed user_id={} error={} message={}",
                    userId, e.getClass().getSimpleName(), e.getMessage(), e);
            try {
                writeService.recordCaptureFailure(
                        userId,
                        "capture_exception",
                        e,
                        "memory-capture-" + shortHash(userId, userMessage, aiResponse));
            } catch (Exception ledgerError) {
                log.warn("memory_capture_event=failure_ledger_write_failed user_id={} error={}",
                        userId, ledgerError.getClass().getSimpleName(), ledgerError);
            }
        }
    }

    private void recordCaptureDecision(String userId,
                                       String status,
                                       String reason,
                                       MemoryCapturePolicy.CaptureDecision decision,
                                       String userMessage) {
        if (!memoryProperties.getAudit().isEnabled()) {
            return;
        }
        try {
            writeService.recordCaptureDecision(userId, status, reason, decision, userMessage);
        } catch (Exception ledgerError) {
            log.warn("memory_capture_event=decision_ledger_write_failed user_id={} status={} reason={} error={}",
                    userId, status, reason, ledgerError.getClass().getSimpleName(), ledgerError);
        }
    }

    private void logCapture(String status,
                            String userId,
                            String reason,
                            int candidateCount,
                            int writeCount,
                            long startedAt) {
        if (!memoryProperties.getAudit().isEnabled()) {
            memoryMetricsService.recordCapture(status, reason, candidateCount, writeCount, elapsedMs(startedAt));
            return;
        }
        long durationMs = elapsedMs(startedAt);
        memoryMetricsService.recordCapture(status, reason, candidateCount, writeCount, durationMs);
        log.info("memory_capture_event={} user_id={} reason={} capture_mode={} candidate_count={} write_count={} duration_ms={}",
                status,
                userId,
                reason,
                memoryProperties.getCapture().getMode().name().toLowerCase(Locale.ROOT),
                candidateCount,
                writeCount,
                durationMs);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String shortHash(String userId, String userMessage, String aiResponse) {
        return sha256(nullSafe(userId) + "\u001f" + nullSafe(userMessage) + "\u001f" + nullSafe(aiResponse))
                .substring(0, 24);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(nullSafe(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
