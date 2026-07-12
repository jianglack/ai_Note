package com.ainote.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSlaCapacityAssetsTest {

    private final Path root = repositoryRoot();

    @Test
    void k6WorkloadDefinesStatefulMixAndHardGates() throws IOException {
        String script = Files.readString(root.resolve("perf/ainote-sla.js"));

        assertThat(script)
                .contains("constant-arrival-rate")
                .contains("/api/ai/chat/save")
                .contains("/api/ai/chat/history?limit=100")
                .contains("/api/memories")
                .contains("/api/notes?page=0&size=25")
                .contains("sla_request_success")
                .contains("exec.scenario.iterationInTest % 10")
                .contains("sla_append_operations")
                .contains("sla_history_operations")
                .contains("p(95)")
                .contains("p(99)")
                .contains("dropped_iterations");
    }

    @Test
    void runnerUsesDisposableInfrastructureAndFailClosedEvidence() throws IOException {
        String runner = Files.readString(root.resolve("scripts/run-sla-capacity.ps1"));

        assertThat(runner)
                .contains("managed-local")
                .contains("pgvector/pgvector:pg15")
                .contains("redis:7-alpine")
                .contains("SPRING_PROFILES_ACTIVE = \"prod\"")
                .contains("Get-IntegrityEvidence")
                .contains("resource_sample_error")
                .contains("process_restart_detected")
                .contains("cleanup_verification_failed")
                .contains("productionProven = $false");
    }

    @Test
    void runnerKeepsCredentialsOutOfTheFinalReport() throws IOException {
        String runner = Files.readString(root.resolve("scripts/run-sla-capacity.ps1"));
        String reportBlock = runner.substring(runner.indexOf("$report = [ordered]@{"));

        assertThat(reportBlock)
                .doesNotContain("response.token")
                .doesNotContain("Authorization")
                .doesNotContain("JWT_SECRET")
                .doesNotContain("ainote_sla_password");
    }

    @Test
    void designSeparatesLocalEvidenceFromProductionProof() throws IOException {
        String design = Files.readString(root.resolve(
                "docs/superpowers/specs/2026-07-11-production-sla-capacity-stability-design.md"));

        assertThat(design)
                .contains("local-production-like")
                .contains("production-proven")
                .contains("Release soak default: 30 minutes")
                .contains("Extended production-candidate soak: 24 hours")
                .contains("exactly 2 per successful append");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (current.getFileName() != null && "backend".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        return current;
    }
}
