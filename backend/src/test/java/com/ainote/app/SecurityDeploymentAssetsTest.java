package com.ainote.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityDeploymentAssetsTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void backendRuntimeImageRunsAsNonRoot() throws IOException {
        String dockerfile = Files.readString(ROOT.resolve("backend/Dockerfile"));

        assertThat(dockerfile).contains("USER ainote");
        assertThat(dockerfile).contains("--chown=ainote:ainote");
    }

    @Test
    void frontendRuntimeIsUnprivilegedAndPublishesSecurityHeaders() throws IOException {
        String dockerfile = Files.readString(ROOT.resolve("frontend/Dockerfile"));
        String nginx = Files.readString(ROOT.resolve("frontend/nginx.conf"));

        assertThat(dockerfile).contains("nginxinc/nginx-unprivileged:stable-alpine");
        assertThat(dockerfile).contains("EXPOSE 8080");
        assertThat(nginx).contains("listen 8080;");
        assertThat(nginx).contains("Content-Security-Policy");
        assertThat(nginx).contains("frame-ancestors 'none'");
        assertThat(nginx).contains("X-Content-Type-Options \"nosniff\"");
        assertThat(nginx).contains("X-Frame-Options \"DENY\"");
        assertThat(nginx).contains("Referrer-Policy");
        assertThat(nginx).contains("Permissions-Policy");
    }

    @Test
    void dynamicSecurityRunnerIsIsolatedRedactedAndFailClosed() throws IOException {
        String runner = Files.readString(ROOT.resolve("scripts/run-security-compliance.ps1"));

        assertThat(runner).contains("^ainote-security-");
        assertThat(runner).contains("Refusing to remove non-security container");
        assertThat(runner).contains("csrf_missing_rejected");
        assertThat(runner).contains("bola_cross_tenant_read_denied");
        assertThat(runner).contains("ordinary_user_admin_denied");
        assertThat(runner).contains("ssrf_loopback_no_outbound_connection");
        assertThat(runner).contains("logout_revokes_server_token");
        assertThat(runner).contains("productionProven = $false");
        assertThat(runner).contains("cleanupPassed = $cleanupPassed");
        assertThat(runner).doesNotContain("docker rm -f ainote-postgres");
    }

    @Test
    void supplyChainRunnerFailsUnavailableAndScopesTheReviewedException() throws IOException {
        String runner = Files.readString(ROOT.resolve("scripts/run-security-sca.ps1"));
        String config = Files.readString(ROOT.resolve("security/osv-scanner.toml"));

        assertThat(runner).contains("OSV Scanner is unavailable");
        assertThat(runner).contains("OSV Scanner checksum verification failed");
        assertThat(runner).contains("npm audit reported a blocking vulnerability or was unavailable");
        assertThat(runner).contains("com.fasterxml.jackson.core:jackson-databind");
        assertThat(runner).contains("2.21.5");
        assertThat(runner).contains("gatedVulnerabilityCount");
        assertThat(config).contains("GHSA-5jmj-h7xm-6q6v");
        assertThat(config).contains("ignoreUntil = 2026-08-11");
        assertThat(config).contains("Owner: AiNote maintainers");
    }

    @Test
    void releaseWorkflowPinsActionsAndUsesTheSupportedNodeRuntime() throws IOException {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/release.yml"));

        assertThat(workflow)
                .contains("actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0")
                .contains("actions/setup-java@0f481fcb613427c0f801b606911222b5b6f3083a # v5.5.0")
                .contains("actions/setup-node@48b55a011bda9f5d6aeb4c2d9c7362e8dae4041e # v6.4.0")
                .contains("actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1")
                .contains("node-version: \"24\"")
                .doesNotContain("actions/checkout@v")
                .doesNotContain("actions/setup-java@v")
                .doesNotContain("actions/setup-node@v")
                .doesNotContain("actions/upload-artifact@v")
                .doesNotContain("node-version: \"20\"");
    }

    @Test
    void complianceEvidenceIsVersionedAndDoesNotOverclaimProduction() throws IOException {
        String matrix = Files.readString(ROOT.resolve("docs/security/security-compliance-matrix.md"));
        String runbook = Files.readString(ROOT.resolve("docs/operations/SECURITY_VERIFICATION.md"));
        String finalReview = Files.readString(ROOT.resolve(
                "docs/superpowers/reviews/2026-07-11-security-penetration-compliance-postimplementation-review.md"));

        assertThat(matrix).contains("PASS", "PARTIAL", "EXTERNAL");
        assertThat(matrix).contains("production-proven", "independent penetration-test certificate");
        assertThat(matrix).contains("2026-08-11");
        assertThat(runbook).contains("run-security-sca.ps1", "run-security-compliance.ps1");
        assertThat(runbook).contains("scanner failure is never equivalent to zero findings");
        assertThat(finalReview).contains("productionProven=false");
        assertThat(finalReview).contains("25 passed, 0 failed");
        assertThat(finalReview).contains("0 gated findings");
    }
}
