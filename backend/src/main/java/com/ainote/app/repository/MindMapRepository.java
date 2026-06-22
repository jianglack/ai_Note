package com.ainote.app.repository;

import com.ainote.app.entity.MindMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MindMapRepository extends JpaRepository<MindMap, String> {
    List<MindMap> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<MindMap> findByIdAndUserId(String id, String userId);
    Optional<MindMap> findByNoteIdAndUserId(String noteId, String userId);
    List<MindMap> findByNoteIdAndUserIdOrderByUpdatedAtDesc(String noteId, String userId);
    List<MindMap> findByNoteId(String noteId);
}
