package com.ainote.app.repository;

import com.ainote.app.entity.SideEffectJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SideEffectJournalRepository extends JpaRepository<SideEffectJournal, String> {

    List<SideEffectJournal> findByPlanIdAndStepIdOrderByCreatedAtDesc(
            String planId, String stepId);
}
