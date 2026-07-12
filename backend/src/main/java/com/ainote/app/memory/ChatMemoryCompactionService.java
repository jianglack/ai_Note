package com.ainote.app.memory;

import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.ChatMemoryCompactionJob;
import com.ainote.app.entity.UserMemory;
import com.ainote.app.repository.ChatMemoryCompactionJobRepository;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.MemoryExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMemoryCompactionService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryCompactionService.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final ChatMemoryCompactionJobRepository jobRepository;
    private final UserMemoryRepository memoryRepository;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final MemoryProperties memoryProperties;
    private final ShortTermMemoryMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public ChatMemoryCompactionService(
            ChatMemoryCompactionJobRepository jobRepository,
            UserMemoryRepository memoryRepository,
            EpisodicMemoryRepository episodicMemoryRepository,
            MemoryExtractionService memoryExtractionService,
            MemoryProperties memoryProperties,
            ShortTermMemoryMetrics metrics,
            PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.memoryRepository = memoryRepository;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.memoryExtractionService = memoryExtractionService;
        this.memoryProperties = memoryProperties;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.memory.chat-history.compaction-poll-ms:5000}")
    public void processPendingJobs() {
        if (!memoryProperties.getChatHistory().isCompactionEnabled()) return;
        int batchSize = memoryProperties.getChatHistory().getCompactionWorkerBatchSize();
        for (int i = 0; i < batchSize; i++) {
            ClaimedJob job = claimNext();
            if (job == null) return;
            process(job);
        }
    }

    private ClaimedJob claimNext() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            List<ChatMemoryCompactionJob> claimable = jobRepository.findClaimable(now, PageRequest.of(0, 1));
            if (claimable.isEmpty()) return null;
            ChatMemoryCompactionJob job = claimable.get(0);
            job.setStatus("processing");
            job.setAttempts(job.getAttempts() + 1);
            job.setLeaseUntil(now.plusSeconds(memoryProperties.getChatHistory().getCompactionLeaseSeconds()));
            job.setNextAttemptAt(null);
            jobRepository.save(job);
            return ClaimedJob.from(job);
        });
    }

    private void process(ClaimedJob job) {
        long startedAt = System.currentTimeMillis();
        String sourceRange = "chat-sequence:" + job.fromSequence() + "-" + job.toSequence();
        try {
            if (!episodicMemoryRepository.existsByUserIdAndSourceMessageRange(job.userId(), sourceRange)) {
                List<UserMemory> rows = memoryRepository.findByUserIdAndSequenceRange(
                        job.userId(), job.fromSequence(), job.toSequence());
                String conversationText = rows.stream()
                        .filter(row -> "USER".equals(row.getMessageType()) || "AI".equals(row.getMessageType()))
                        .filter(row -> row.getContent() != null && !row.getContent().isBlank())
                        .map(row -> ("USER".equals(row.getMessageType()) ? "User: " : "AI: ")
                                + row.getContent())
                        .collect(Collectors.joining("\n"));
                if (!conversationText.isBlank()) {
                    memoryExtractionService.generateEpisodicSummaryFromTextSync(
                            job.userId(), conversationText, job.userMessageCount(), sourceRange);
                }
            }
            boolean completed = complete(job);
            metrics.recordCompaction(completed ? "completed" : "stale", System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            boolean failed = fail(job, e);
            metrics.recordCompaction(failed ? "failed" : "stale", System.currentTimeMillis() - startedAt);
        }
    }

    private boolean complete(ClaimedJob claimed) {
        Boolean completed = transactionTemplate.execute(status -> jobRepository.findById(claimed.id())
                .map(job -> {
            if (!isCurrentClaim(job, claimed)) return false;
            job.setStatus("completed");
            job.setLeaseUntil(null);
            job.setNextAttemptAt(null);
            job.setLastError(null);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            return true;
        }).orElse(false));
        return Boolean.TRUE.equals(completed);
    }

    private boolean fail(ClaimedJob claimed, RuntimeException failure) {
        Boolean failed = transactionTemplate.execute(status -> jobRepository.findById(claimed.id())
                .map(job -> {
            if (!isCurrentClaim(job, claimed)) return false;
            boolean terminal = job.getAttempts() >= memoryProperties.getChatHistory().getCompactionMaxAttempts();
            job.setStatus(terminal ? "dead_letter" : "retry");
            job.setLeaseUntil(null);
            job.setNextAttemptAt(terminal ? null : LocalDateTime.now().plusSeconds(backoffSeconds(job.getAttempts())));
            job.setLastError(truncateError(failure));
            jobRepository.save(job);
            log.warn("Chat memory compaction failed jobId={} user={} range={}-{} attempts={} status={}",
                    job.getId(), job.getUserId(), job.getFromSequence(), job.getToSequence(),
                    job.getAttempts(), job.getStatus());
            return true;
        }).orElse(false));
        return Boolean.TRUE.equals(failed);
    }

    private boolean isCurrentClaim(ChatMemoryCompactionJob job, ClaimedJob claimed) {
        return "processing".equals(job.getStatus())
                && job.getAttempts() != null
                && job.getAttempts() == claimed.attempt();
    }

    private long backoffSeconds(int attempts) {
        int exponent = Math.max(0, Math.min(9, attempts - 1));
        return Math.min(3600, 5L * (1L << exponent));
    }

    private String truncateError(RuntimeException failure) {
        String message = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "unknown" : failure.getMessage());
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private record ClaimedJob(
            Long id,
            String userId,
            int fromSequence,
            int toSequence,
            int userMessageCount,
            int attempt) {
        static ClaimedJob from(ChatMemoryCompactionJob job) {
            return new ClaimedJob(job.getId(), job.getUserId(), job.getFromSequence(),
                    job.getToSequence(), job.getUserMessageCount(), job.getAttempts());
        }
    }
}
