package com.ainote.app.service.planning;

import com.ainote.app.entity.SideEffectJournal;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.repository.SideEffectJournalRepository;
import com.ainote.app.repository.TaskStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlanStepCompletionRecorderTest {

    @Test
    void recordSuccess_isTransactionalAndPersistsStepAndJournalTogether() throws Exception {
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        SideEffectJournalRepository journalRepository = mock(SideEffectJournalRepository.class);
        PlanStepCompletionRecorder recorder = new PlanStepCompletionRecorder(
                stepRepository,
                journalRepository);
        TaskStep step = new TaskStep();
        step.setId("step-1");
        SideEffectJournal journal = new SideEffectJournal();
        journal.setId("journal-1");

        recorder.recordSuccess(step, Optional.of(journal));

        verify(stepRepository).save(step);
        verify(journalRepository).save(journal);
        Method method = PlanStepCompletionRecorder.class.getMethod(
                "recordSuccess",
                TaskStep.class,
                Optional.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }
}
