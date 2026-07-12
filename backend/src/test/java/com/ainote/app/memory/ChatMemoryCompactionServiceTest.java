package com.ainote.app.memory;

import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.ChatMemoryCompactionJob;
import com.ainote.app.entity.UserMemory;
import com.ainote.app.repository.ChatMemoryCompactionJobRepository;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.MemoryExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMemoryCompactionServiceTest {

    private ChatMemoryCompactionJobRepository jobRepository;
    private UserMemoryRepository memoryRepository;
    private EpisodicMemoryRepository episodicMemoryRepository;
    private MemoryExtractionService extractionService;
    private ShortTermMemoryMetrics metrics;
    private ChatMemoryCompactionService service;
    private ChatMemoryCompactionJob job;

    @BeforeEach
    void setUp() {
        jobRepository = mock(ChatMemoryCompactionJobRepository.class);
        memoryRepository = mock(UserMemoryRepository.class);
        episodicMemoryRepository = mock(EpisodicMemoryRepository.class);
        extractionService = mock(MemoryExtractionService.class);
        metrics = mock(ShortTermMemoryMetrics.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        MemoryProperties properties = new MemoryProperties();
        properties.getChatHistory().setCompactionWorkerBatchSize(1);
        properties.getChatHistory().setCompactionMaxAttempts(5);
        service = new ChatMemoryCompactionService(
                jobRepository, memoryRepository, episodicMemoryRepository,
                extractionService, properties, metrics, transactionManager);

        job = new ChatMemoryCompactionJob();
        ReflectionTestUtils.setField(job, "id", 41L);
        job.setUserId("user-1");
        job.setFromSequence(0);
        job.setToSequence(3);
        job.setUserMessageCount(2);
        when(jobRepository.findById(41L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(ChatMemoryCompactionJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memoryRepository.findByUserIdAndSequenceRange("user-1", 0, 3))
                .thenReturn(List.of(memory("USER", "question", 0), memory("AI", "answer", 1)));
        when(episodicMemoryRepository.existsByUserIdAndSourceMessageRange(anyString(), anyString()))
                .thenReturn(false);
    }

    @Test
    void retriesTwoFailuresAndCompletesOnThirdAttemptWithoutDeletingSource() {
        doThrow(new IllegalStateException("model unavailable"))
                .doThrow(new IllegalStateException("invalid model output"))
                .doNothing()
                .when(extractionService)
                .generateEpisodicSummaryFromTextSync(anyString(), anyString(), anyInt(), anyString());

        runOneClaim();
        assertThat(job.getStatus()).isEqualTo("retry");
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isNotNull();

        runOneClaim();
        assertThat(job.getStatus()).isEqualTo("retry");
        assertThat(job.getAttempts()).isEqualTo(2);

        runOneClaim();
        assertThat(job.getStatus()).isEqualTo("completed");
        assertThat(job.getAttempts()).isEqualTo(3);
        assertThat(job.getCompletedAt()).isNotNull();

        verify(memoryRepository, never()).deleteByUserId(anyString());
        verify(metrics).recordCompaction(eq("completed"), anyLong());
    }

    @Test
    void staleWorkerCannotOverwriteAReclaimedJob() {
        doAnswer(invocation -> {
            job.setAttempts(2);
            job.setStatus("processing");
            return null;
        }).when(extractionService)
                .generateEpisodicSummaryFromTextSync(anyString(), anyString(), anyInt(), anyString());

        runOneClaim();

        assertThat(job.getStatus()).isEqualTo("processing");
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getCompletedAt()).isNull();
        verify(metrics).recordCompaction(eq("stale"), anyLong());
    }

    private void runOneClaim() {
        when(jobRepository.findClaimable(any(), any())).thenReturn(List.of(job));
        service.processPendingJobs();
    }

    private UserMemory memory(String type, String content, int sequence) {
        UserMemory row = new UserMemory();
        row.setUserId("user-1");
        row.setMessageType(type);
        row.setContent(content);
        row.setSequenceNumber(sequence);
        return row;
    }
}
