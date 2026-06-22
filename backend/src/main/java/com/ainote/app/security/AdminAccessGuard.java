package com.ainote.app.security;

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

    public AdminAccessGuard(
            @Value("${app.admin.user-ids:}") String adminUserIdsCsv,
            SecurityUtils securityUtils) {
        this.securityUtils = securityUtils;
        this.adminUserIds = (adminUserIdsCsv == null || adminUserIdsCsv.isBlank())
                ? Set.of()
                : Stream.of(adminUserIdsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    public void checkAdminAccess() {
        String currentUserId = securityUtils.getCurrentUserId();
        if (!adminUserIds.contains(currentUserId)) {
            throw new AccessDeniedException("\u6743\u9650\u4e0d\u8db3");
        }
    }
}
