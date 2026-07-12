package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryCompliancePostureResponse;
import com.ainote.app.model.memory.MemoryRetentionPurgeResponse;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.service.MemoryControlService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/memory-privacy")
@Tag(name = "Memory Privacy Admin", description = "Admin memory privacy and retention operations")
@SecurityRequirement(name = "Bearer Authentication")
public class MemoryPrivacyAdminController {

    private final MemoryControlService memoryControlService;
    private final AdminAccessGuard adminAccessGuard;

    public MemoryPrivacyAdminController(MemoryControlService memoryControlService,
                                        AdminAccessGuard adminAccessGuard) {
        this.memoryControlService = memoryControlService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @PostMapping("/retention/purge")
    public MemoryRetentionPurgeResponse purgeExpiredDeletedMemories() {
        adminAccessGuard.checkAdminAccess("memory_privacy_retention_purge");
        return memoryControlService.purgeExpiredDeletedMemories(LocalDateTime.now());
    }

    @GetMapping("/posture")
    public MemoryCompliancePostureResponse compliancePosture() {
        adminAccessGuard.checkAdminAccess("memory_privacy_posture_read");
        return memoryControlService.compliancePosture();
    }
}
