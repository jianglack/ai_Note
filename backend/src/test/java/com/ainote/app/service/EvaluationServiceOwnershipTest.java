package com.ainote.app.service;

import com.ainote.app.entity.EvalDataset;
import com.ainote.app.entity.EvalItem;
import com.ainote.app.entity.EvalRun;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.EvalDatasetRepository;
import com.ainote.app.repository.EvalItemRepository;
import com.ainote.app.repository.EvalResultRepository;
import com.ainote.app.repository.EvalRunRepository;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceOwnershipTest {

    @Mock
    private EvalDatasetRepository datasetRepository;

    @Mock
    private EvalItemRepository itemRepository;

    @Mock
    private EvalRunRepository runRepository;

    @Mock
    private EvalResultRepository resultRepository;

    @Mock
    private AgentTraceRepository agentTraceRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private LangChain4jRagService ragService;

    @Mock
    private AgentService agentService;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(
                datasetRepository,
                itemRepository,
                runRepository,
                resultRepository,
                agentTraceRepository,
                securityUtils,
                ragService,
                agentService,
                new ObjectMapper());
    }

    @Test
    void getDataset_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(datasetRepository.findByIdAndUserId("dataset-1", "user-1"))
                .thenReturn(Optional.of(dataset("dataset-1")));

        service.getDataset("dataset-1");

        verify(datasetRepository).findByIdAndUserId("dataset-1", "user-1");
        verify(datasetRepository, never()).findById("dataset-1");
    }

    @Test
    void deleteDataset_deletesOnlyOwnedDataset() {
        EvalDataset dataset = dataset("dataset-1");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(datasetRepository.findByIdAndUserId("dataset-1", "user-1")).thenReturn(Optional.of(dataset));

        service.deleteDataset("dataset-1");

        verify(datasetRepository).delete(dataset);
        verify(datasetRepository, never()).deleteById("dataset-1");
    }

    @Test
    void addItem_rejectsDatasetNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(datasetRepository.findByIdAndUserId("dataset-2", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem("dataset-2", "question", "answer", "[]"))
                .isInstanceOf(NoSuchElementException.class);

        verify(itemRepository, never()).save(any());
    }

    @Test
    void deleteItem_rejectsItemWhoseDatasetIsNotOwnedByCurrentUser() {
        EvalItem item = new EvalItem();
        item.setId("item-1");
        item.setDatasetId("dataset-2");
        when(itemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(datasetRepository.findByIdAndUserId("dataset-2", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteItem("item-1"))
                .isInstanceOf(NoSuchElementException.class);

        verify(itemRepository, never()).delete(any());
    }

    @Test
    void runEvaluation_rejectsDatasetNotOwnedByProvidedUserBeforeCreatingRun() {
        when(datasetRepository.findByIdAndUserId("dataset-2", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runEvaluation("dataset-2", "full", "{}", "user-1"))
                .isInstanceOf(NoSuchElementException.class);

        verify(runRepository, never()).save(any());
    }

    @Test
    void getRun_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(runRepository.findByIdAndUserId("run-1", "user-1")).thenReturn(Optional.of(run("run-1")));

        service.getRun("run-1");

        verify(runRepository).findByIdAndUserId("run-1", "user-1");
        verify(runRepository, never()).findById("run-1");
    }

    @Test
    void getResults_validatesRunOwnershipBeforeReturningResults() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(runRepository.findByIdAndUserId("run-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResults("run-1"))
                .isInstanceOf(NoSuchElementException.class);

        verify(resultRepository, never()).findByRunId("run-1");
    }

    private EvalDataset dataset(String id) {
        EvalDataset dataset = new EvalDataset();
        dataset.setId(id);
        dataset.setUserId("user-1");
        dataset.setName("dataset");
        dataset.setDatasetType("rag");
        return dataset;
    }

    private EvalRun run(String id) {
        EvalRun run = new EvalRun();
        run.setId(id);
        run.setUserId("user-1");
        run.setDatasetId("dataset-1");
        run.setRunType("full");
        return run;
    }
}
