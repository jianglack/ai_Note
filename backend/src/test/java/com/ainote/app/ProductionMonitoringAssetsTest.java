package com.ainote.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionMonitoringAssetsTest {

    private final Path root = repositoryRoot();

    @Test
    void prometheusRulesHaveOwnershipRunbooksAndDeterministicTests() throws IOException {
        String rules = Files.readString(root.resolve("observability/prometheus/rules/ainote-alerts.yml"));
        String tests = Files.readString(root.resolve("observability/prometheus/tests/ainote-alerts.test.yml"));

        assertThat(rules)
                .contains("AiNoteBackendDown")
                .contains("AiNoteHighHttp5xxRatio")
                .contains("AiNoteMemoryFlushFailure")
                .contains("AiNoteAlertmanagerNotificationFailure")
                .contains("owner: ainote-maintainers")
                .contains("runbook_url: docs/operations/MONITORING_ALERTING.md");
        assertThat(tests)
                .contains("backend-down-fires-after-hold-period")
                .contains("healthy-backend-does-not-page")
                .contains("low-volume-http-errors-do-not-page")
                .contains("memory-flush-failure-pages")
                .contains("notification-failure-pages");
    }

    @Test
    void scrapeAndNotificationConfigurationKeepSecretsOutOfGit() throws IOException {
        String prometheus = Files.readString(root.resolve("observability/prometheus/prometheus.yml"));
        String compose = Files.readString(root.resolve("observability/docker-compose.yml"));
        String productionAlertmanager = Files.readString(root.resolve(
                "observability/alertmanager/alertmanager.production.yml.example"));

        assertThat(prometheus)
                .contains("X-AiNote-Monitoring-Token")
                .contains("/run/secrets/prometheus_scrape_token")
                .doesNotContain("Authorization: Bearer");
        assertThat(compose)
                .contains("alertmanager.production.yml")
                .contains("GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?set GRAFANA_ADMIN_PASSWORD}");
        assertThat(productionAlertmanager)
                .contains("url_file: /run/secrets/critical_alert_webhook_url")
                .contains("send_resolved: true")
                .doesNotContain("hooks.slack.com")
                .doesNotContain("api.telegram.org");
    }

    @Test
    void grafanaDashboardAndProvisioningAreVersionedAndParseable() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode dashboard = mapper.readTree(Files.readString(root.resolve(
                "observability/grafana/dashboards/ainote-operations.json")));
        String datasource = Files.readString(root.resolve(
                "observability/grafana/provisioning/datasources/prometheus.yml"));

        assertThat(dashboard.path("uid").asText()).isEqualTo("ainote-operations");
        assertThat(dashboard.path("title").asText()).isEqualTo("AiNote Operations");
        assertThat(dashboard.path("panels").size()).isGreaterThanOrEqualTo(9);
        assertThat(datasource).contains("uid: ainote-prometheus", "http://prometheus:9090");
    }

    @Test
    void drillIsNamespacedFailClosedRedactedAndCleansUp() throws IOException {
        String drill = Files.readString(root.resolve("scripts/run-monitoring-incident-drill.ps1"));

        assertThat(drill)
                .contains("^ainote-observability-")
                .contains("Refusing to remove non-drill container")
                .contains("\"/bin/promtool\"")
                .contains("\"test\", \"rules\"")
                .contains("check-config")
                .contains("firingNotificationDelivered")
                .contains("resolvedNotificationDelivered")
                .contains("secretAbsentFromEvidence")
                .contains("productionProven = $false")
                .contains("cleanupPassed")
                .doesNotContain("docker rm -f ainote-postgres");
    }

    @Test
    void runbooksDefineSeverityOwnershipCommunicationAndNonClaims() throws IOException {
        String monitoring = Files.readString(root.resolve("docs/operations/MONITORING_ALERTING.md"));
        String incident = Files.readString(root.resolve("docs/operations/INCIDENT_RESPONSE.md"));

        assertThat(monitoring)
                .contains("AiNoteBackendDown")
                .contains("AiNoteMemoryFlushFailure")
                .contains("does not claim that a real paging provider")
                .contains("run-monitoring-incident-drill.ps1");
        assertThat(incident)
                .contains("SEV-1", "5 minutes", "Incident commander")
                .contains("Communication cadence")
                .contains("Post-Incident Review")
                .contains("must not contain credentials");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (current.getFileName() != null && "backend".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        return current;
    }
}
