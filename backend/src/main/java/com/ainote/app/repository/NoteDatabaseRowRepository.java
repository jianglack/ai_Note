package com.ainote.app.repository;

import com.ainote.app.entity.NoteDatabaseRow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NoteDatabaseRowRepository extends JpaRepository<NoteDatabaseRow, String> {
    List<NoteDatabaseRow> findByDatabaseIdOrderBySortOrderAsc(String databaseId);
    Optional<NoteDatabaseRow> findByIdAndDatabaseId(String id, String databaseId);
    void deleteByDatabaseId(String databaseId);
}
