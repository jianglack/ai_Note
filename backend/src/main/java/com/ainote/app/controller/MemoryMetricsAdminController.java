package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryMetricsSnapshotResponse;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.service.MemoryMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/memory-metrics")
@Tag(name = "Memory Metrics Admin", description = "Administrative memory production metrics")
@SecurityRequirement(name = "Bearer Authentication")
public class MemoryMetricsAdminController {

    private final MemoryMetricsService memoryMetricsService;
    private final AdminAccessGuard adminAccessGuard;

    public MemoryMetricsAdminController(MemoryMetricsService memoryMetricsService,
                                        AdminAccessGuard adminAccessGuard) {
        this.memoryMetricsService = memoryMetricsService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping
    @Operation(summary = "Get memory production metrics")
    public MemoryMetricsSnapshotResponse snapshot() {
        adminAccessGuard.checkAdminAccess("memory_metrics_read");
        return memoryMetricsService.snapshot();
    }
}
