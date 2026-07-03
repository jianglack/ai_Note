package com.ainote.app.repository;

import com.ainote.app.entity.MemoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryEventRepository extends JpaRepository<MemoryEvent, Long> {

    List<MemoryEvent> findByUserIdOrderByCreatedAtDesc(String userId);

    List<MemoryEvent> findByMemoryIdOrderByCreatedAtDesc(Long memoryId);
}
