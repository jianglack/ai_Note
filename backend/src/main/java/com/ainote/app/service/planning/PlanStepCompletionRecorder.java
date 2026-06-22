package com.ainote.app.service.planning;

import com.ainote.app.entity.SideEffectJournal;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.repository.SideEffectJournalRepository;
import com.ainote.app.repository.TaskStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PlanStepCompletionRecorder {

    private final TaskStepRepository stepRepository;
    private final SideEffectJournalRepository sideEffectJournalRepository;

    public PlanStepCompletionRecorder(TaskStepRepository stepRepository,
                                      SideEffectJournalRepository sideEffectJournalRepository) {
        this.stepRepository = stepRepository;
        this.sideEffectJournalRepository = sideEffectJournalRepository;
    }

    @Transactional
    public void recordSuccess(TaskStep step, Optional<SideEffectJournal> sideEffectJournal) {
        stepRepository.save(step);
        sideEffectJournal.ifPresent(sideEffectJournalRepository::save);
    }
}
