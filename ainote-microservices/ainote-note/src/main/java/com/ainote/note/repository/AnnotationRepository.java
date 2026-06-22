package com.ainote.note.repository;

import com.ainote.note.entity.Annotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnotationRepository extends JpaRepository<Annotation, String> {

    List<Annotation> findByNoteIdOrderByStartOffsetAsc(String noteId);

    List<Annotation> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByNoteId(String noteId);
}
