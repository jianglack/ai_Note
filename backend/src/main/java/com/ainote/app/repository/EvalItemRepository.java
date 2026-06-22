package com.ainote.app.repository;

import com.ainote.app.entity.EvalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvalItemRepository extends JpaRepository<EvalItem, String> {
    List<EvalItem> findByDatasetId(String datasetId);
    int countByDatasetId(String datasetId);
}
