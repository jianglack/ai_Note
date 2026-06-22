package com.ainote.app.repository;

import com.ainote.app.entity.Canvas;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CanvasRepository extends JpaRepository<Canvas, String> {
    List<Canvas> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<Canvas> findByIdAndUserId(String id, String userId);
}
