package com.ainote.app.security;

import com.ainote.app.entity.AdminAccessAudit;
import com.ainote.app.repository.AdminAccessAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class AdminAccessGuard {

    private final Set<String> adminUserIds;
    private final SecurityUtils securityUtils;
    private final AdminAccessAuditRepository adminAccessAuditRepository;

    @Autowired
    public AdminAccessGuard(
            @Value("${app.admin.user-ids:}") String adminUserIdsCsv,
            SecurityUtils securityUtils,
            AdminAccessAuditRepository adminAccessAuditRepository) {
        this.securityUtils = securityUtils;
        this.adminAccessAuditRepository = adminAccessAuditRepository;
        this.adminUserIds = (adminUserIdsCsv == null || adminUserIdsCsv.isBlank())
                ? Set.of()
                : Stream.of(adminUserIdsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    public AdminAccessGuard(String adminUserIdsCsv, SecurityUtils securityUtils) {
        this(adminUserIdsCsv, securityUtils, null);
    }

    public void checkAdminAccess() {
        checkAdminAccess("admin_access");
    }

    public void checkAdminAccess(String action) {
        String currentUserId = securityUtils.getCurrentUserId();
        boolean allowed = adminUserIds.contains(currentUserId);
        recordAudit(currentUserId, normalizeAction(action), allowed);
        if (!allowed) {
            throw new AccessDeniedException("\u6743\u9650\u4e0d\u8db3");
        }
    }

    private void recordAudit(String userId, String action, boolean allowed) {
        if (adminAccessAuditRepository == null) {
            return;
        }
        try {
            AdminAccessAudit audit = new AdminAccessAudit();
            audit.setUserId(userId == null || userId.isBlank() ? "unknown" : userId);
            audit.setAction(action);
            audit.setDecision(allowed ? "allowed" : "denied");
            audit.setReason(allowed ? "allowlisted_admin_user" : "user_not_in_admin_allowlist");
            adminAccessAuditRepository.save(audit);
        } catch (RuntimeException ignored) {
            // Authorization must not be bypassed or blocked by audit persistence failure.
        }
    }

    private String normalizeAction(String action) {
        return action == null || action.isBlank() ? "admin_access" : action.trim();
    }
}
