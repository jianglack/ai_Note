package com.ainote.app.repository;

import com.ainote.app.entity.AdminAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminAccessAuditRepository extends JpaRepository<AdminAccessAudit, Long> {
}
