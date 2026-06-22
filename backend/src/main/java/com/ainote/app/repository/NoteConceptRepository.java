package com.ainote.app.repository;

import com.ainote.app.entity.NoteConcept;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteConceptRepository extends JpaRepository<NoteConcept, Long> {

    List<NoteConcept> findByNoteId(String noteId);

    List<NoteConcept> findByUserId(String userId);

    @Modifying
    @Query("DELETE FROM NoteConcept nc WHERE nc.noteId = :noteId")
    void deleteByNoteId(String noteId);

    @Query("SELECT DISTINCT nc.concept FROM NoteConcept nc WHERE nc.userId = :userId")
    List<String> findDistinctConceptsByUserId(String userId);

    @Query("SELECT COUNT(DISTINCT nc.concept) FROM NoteConcept nc WHERE nc.userId = :userId")
    long countDistinctConceptsByUserId(@Param("userId") String userId);

    @Query("SELECT nc.concept FROM NoteConcept nc " +
            "WHERE nc.userId = :userId " +
            "GROUP BY nc.concept " +
            "ORDER BY COUNT(nc) DESC")
    List<String> findTopConceptsByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT nc FROM NoteConcept nc WHERE nc.userId = :userId AND nc.concept IN :concepts")
    List<NoteConcept> findByUserIdAndConceptIn(String userId, List<String> concepts);

    @Query("SELECT nc.noteId FROM NoteConcept nc WHERE nc.userId = :userId AND nc.concept = :concept")
    List<String> findNoteIdsByUserIdAndConcept(String userId, String concept);
}
