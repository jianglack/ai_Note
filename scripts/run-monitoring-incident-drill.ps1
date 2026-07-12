param(
    [string]$ReportDir = "backend/target/monitoring-incident-drill"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$prometheusImage = "prom/prometheus:v3.13.0@sha256:c6b27ea434f8389bfe233fbc7be381cf50587c286e871bc842008f5a1b1908a7"
$alertmanagerImage = "prom/alertmanager:v0.32.1@sha256:51a825c2a40acc3e338fdd00d622e01ec090f72be2b3ea46be0839cd47a4d286"
$grafanaImage = "grafana/grafana:13.0.2@sha256:5dad0df181cb644a14e13617b913b261a54f7d4fd4510721dba420929f35bea2"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resolvedReportDir = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $ReportDir))
$runId = "ainote-observability-" + (Get-Date -Format "yyyyMMddHHmmss") + "-" + (Get-Random -Minimum 10000 -Maximum 99999)
$tempRoot = Join-Path $resolvedReportDir $runId
$networkName = $runId
$prometheusName = "$runId-prometheus"
$alertmanagerName = "$runId-alertmanager"
$grafanaName = "$runId-grafana"
$receiverProcess = $null
$failure = $null
$cleanupPassed = $false
$startedAt = (Get-Date).ToUniversalTime()

$checks = [ordered]@{
    prometheusConfigValid = $false
    prometheusRulesValid = $false
    alertmanagerConfigValid = $false
    grafanaJsonValid = $false
    prometheusReady = $false
    alertmanagerReady = $false
    grafanaReady = $false
    grafanaDatasourceProvisioned = $false
    grafanaDashboardProvisioned = $false
    firingNotificationDelivered = $false
    resolvedNotificationDelivered = $false
    secretAbsentFromEvidence = $false
}

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($exitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return @($output)
}

function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try {
        return ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [hashtable]$Headers = @{},
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $Url -Headers $Headers -TimeoutSec 5 | Out-Null
            return $true
        } catch {
            Start-Sleep -Milliseconds 750
        }
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Wait-ReceiverEvent {
    param(
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string]$DrillId,
        [Parameter(Mandatory = $true)][ValidateSet("firing", "resolved")][string]$Status,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (Test-Path -LiteralPath $LogPath) {
            $content = Get-Content -LiteralPath $LogPath -Raw -ErrorAction SilentlyContinue
            if ($content -match [regex]::Escape($DrillId) -and $content -match ('"status"\s*:\s*"' + $Status + '"')) {
                return $true
            }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Remove-DrillContainer {
    param([string]$Name)
    if ($Name -notmatch '^ainote-observability-[0-9]+-[0-9]+-(prometheus|alertmanager|grafana)$') {
        throw "Refusing to remove non-drill container: $Name"
    }
    $existing = & docker ps -a --filter "name=^/$Name$" --format "{{.Names}}" 2>$null
    if ($existing -eq $Name) {
        & docker rm -f $Name 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to remove drill container $Name"
        }
    }
}

function Remove-DrillNetwork {
    param([string]$Name)
    if ($Name -notmatch '^ainote-observability-[0-9]+-[0-9]+$') {
        throw "Refusing to remove non-drill network: $Name"
    }
    $existing = & docker network ls --filter "name=^$Name$" --format "{{.Name}}" 2>$null
    if ($existing -eq $Name) {
        & docker network rm $Name 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to remove drill network $Name"
        }
    }
}

function New-RandomSecret {
    param([int]$Bytes = 48)
    $buffer = New-Object byte[] $Bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    try {
        return [Convert]::ToBase64String($buffer)
    } finally {
        [Array]::Clear($buffer, 0, $buffer.Length)
    }
}

New-Item -ItemType Directory -Path $resolvedReportDir -Force | Out-Null
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

$receiverPort = Get-FreeTcpPort
$alertmanagerPort = Get-FreeTcpPort
$prometheusPort = Get-FreeTcpPort
$grafanaPort = Get-FreeTcpPort
$drillAlertId = "drill-" + [Guid]::NewGuid().ToString("N")
$monitoringToken = New-RandomSecret
$grafanaPassword = New-RandomSecret
$receiverLog = Join-Path $tempRoot "receiver-events.jsonl"

try {
    & docker version --format "{{.Server.Version}}" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Engine is unavailable"
    }
    $javacExecutable = (Get-Command javac -ErrorAction Stop).Source
    $javaExecutable = Join-Path (Split-Path -Parent $javacExecutable) "java.exe"
    if (-not (Test-Path -LiteralPath $javaExecutable)) {
        throw "Matching Java runtime was not found beside javac"
    }
    $javaVersion = (Get-Item -LiteralPath $javaExecutable).VersionInfo.ProductVersion
    if ($javaVersion -notmatch '^(17|18|19|2[0-9])\.') {
        throw "Java 17 or newer is required for the webhook receiver"
    }

    $prometheusRoot = Join-Path $tempRoot "prometheus"
    $prometheusRules = Join-Path $prometheusRoot "rules"
    $prometheusTests = Join-Path $prometheusRoot "tests"
    $prometheusTargets = Join-Path $prometheusRoot "targets"
    $secretRoot = Join-Path $tempRoot "secrets"
    New-Item -ItemType Directory -Path $prometheusRules, $prometheusTests, $prometheusTargets, $secretRoot -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repositoryRoot "observability/prometheus/prometheus.yml") -Destination $prometheusRoot
    Copy-Item -LiteralPath (Join-Path $repositoryRoot "observability/prometheus/rules/ainote-alerts.yml") -Destination $prometheusRules
    Copy-Item -LiteralPath (Join-Path $repositoryRoot "observability/prometheus/tests/ainote-alerts.test.yml") -Destination $prometheusTests
    [IO.File]::WriteAllText((Join-Path $prometheusTargets "ainote.json"), "[]", (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText((Join-Path $secretRoot "prometheus_scrape_token"), $monitoringToken, (New-Object Text.UTF8Encoding($false)))

    $alertmanagerTemplate = Get-Content -LiteralPath (Join-Path $repositoryRoot "observability/alertmanager/alertmanager.yml") -Raw
    $alertmanagerConfig = $alertmanagerTemplate.Replace("host.docker.internal:19099", "host.docker.internal:$receiverPort")
    $alertmanagerPath = Join-Path $tempRoot "alertmanager.yml"
    [IO.File]::WriteAllText($alertmanagerPath, $alertmanagerConfig, (New-Object Text.UTF8Encoding($false)))

    $receiverSource = @'
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class WebhookReceiver {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        Path log = Path.of(args[1]);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", exchange -> handle(exchange, log));
        server.start();
    }

    private static void handle(HttpExchange exchange, Path log) throws java.io.IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            synchronized (WebhookReceiver.class) {
                Files.writeString(log, new String(body, StandardCharsets.UTF_8) + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().close();
    }
}
'@
    $receiverJava = Join-Path $tempRoot "WebhookReceiver.java"
    $receiverStdout = Join-Path $tempRoot "receiver.out.log"
    $receiverStderr = Join-Path $tempRoot "receiver.err.log"
    [IO.File]::WriteAllText($receiverJava, $receiverSource, (New-Object Text.UTF8Encoding($false)))
    & $javacExecutable --add-modules jdk.httpserver -d $tempRoot $receiverJava 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Webhook receiver compilation failed"
    }
    $receiverProcess = Start-Process -FilePath $javaExecutable -ArgumentList @(
        "--add-modules", "jdk.httpserver", "-cp", $tempRoot, "WebhookReceiver", $receiverPort, $receiverLog
    ) -WindowStyle Hidden -RedirectStandardOutput $receiverStdout -RedirectStandardError $receiverStderr -PassThru
    Start-Sleep -Seconds 1
    if ($receiverProcess.HasExited) {
        $receiverError = if (Test-Path -LiteralPath $receiverStderr) {
            (Get-Content -LiteralPath $receiverStderr -Raw).Trim()
        } else {
            "no stderr"
        }
        throw "Webhook receiver exited with code $($receiverProcess.ExitCode): $receiverError"
    }
    if (-not (Wait-HttpReady -Url "http://127.0.0.1:$receiverPort/health" -TimeoutSeconds 30)) {
        throw "Webhook receiver did not become ready"
    }

    $prometheusMount = "${prometheusRoot}:/etc/prometheus:ro"
    $tokenMount = "$(Join-Path $secretRoot 'prometheus_scrape_token'):/run/secrets/prometheus_scrape_token:ro"
    Invoke-Docker -Arguments @("run", "--rm", "--entrypoint", "/bin/promtool", "-v", $prometheusMount, "-v", $tokenMount,
        $prometheusImage, "check", "config", "/etc/prometheus/prometheus.yml") | Out-Null
    $checks.prometheusConfigValid = $true
    Invoke-Docker -Arguments @("run", "--rm", "--entrypoint", "/bin/promtool", "-v", $prometheusMount,
        $prometheusImage, "test", "rules", "/etc/prometheus/tests/ainote-alerts.test.yml") | Out-Null
    $checks.prometheusRulesValid = $true

    $alertmanagerMount = "${alertmanagerPath}:/etc/alertmanager/alertmanager.yml:ro"
    Invoke-Docker -Arguments @("run", "--rm", "--entrypoint", "/bin/amtool", "-v", $alertmanagerMount,
        $alertmanagerImage, "check-config", "--enable-feature=utf8-strict-mode", "/etc/alertmanager/alertmanager.yml") | Out-Null
    $checks.alertmanagerConfigValid = $true

    Get-Content -LiteralPath (Join-Path $repositoryRoot "observability/grafana/dashboards/ainote-operations.json") -Raw |
        ConvertFrom-Json | Out-Null
    $checks.grafanaJsonValid = $true

    Invoke-Docker -Arguments @("network", "create", $networkName) | Out-Null
    Invoke-Docker -Arguments @("run", "-d", "--name", $alertmanagerName, "--network", $networkName,
        "-p", "127.0.0.1:${alertmanagerPort}:9093", "-v", $alertmanagerMount, $alertmanagerImage,
        "--config.file=/etc/alertmanager/alertmanager.yml", "--storage.path=/alertmanager",
        "--enable-feature=utf8-strict-mode") | Out-Null
    Invoke-Docker -Arguments @("run", "-d", "--name", $prometheusName, "--network", $networkName,
        "-p", "127.0.0.1:${prometheusPort}:9090", "-v", $prometheusMount, "-v", $tokenMount,
        $prometheusImage, "--config.file=/etc/prometheus/prometheus.yml", "--storage.tsdb.path=/prometheus") | Out-Null

    $datasourceMount = "$(Join-Path $repositoryRoot 'observability/grafana/provisioning'):/etc/grafana/provisioning:ro"
    $dashboardMount = "$(Join-Path $repositoryRoot 'observability/grafana/dashboards'):/var/lib/grafana/dashboards:ro"
    Invoke-Docker -Arguments @("run", "-d", "--name", $grafanaName, "--network", $networkName,
        "-p", "127.0.0.1:${grafanaPort}:3000", "-e", "GF_SECURITY_ADMIN_USER=admin",
        "-e", "GF_SECURITY_ADMIN_PASSWORD=$grafanaPassword", "-e", "GF_USERS_ALLOW_SIGN_UP=false",
        "-e", "GF_AUTH_ANONYMOUS_ENABLED=false", "-v", $datasourceMount, "-v", $dashboardMount,
        $grafanaImage) | Out-Null

    $checks.alertmanagerReady = Wait-HttpReady -Url "http://127.0.0.1:$alertmanagerPort/-/ready"
    $checks.prometheusReady = Wait-HttpReady -Url "http://127.0.0.1:$prometheusPort/-/ready"
    $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$grafanaPassword"))
    $grafanaHeaders = @{ Authorization = "Basic $basic" }
    $checks.grafanaReady = Wait-HttpReady -Url "http://127.0.0.1:$grafanaPort/api/health" -Headers $grafanaHeaders
    if (-not ($checks.alertmanagerReady -and $checks.prometheusReady -and $checks.grafanaReady)) {
        throw "One or more observability services failed readiness"
    }

    $datasources = Invoke-RestMethod -Uri "http://127.0.0.1:$grafanaPort/api/datasources" -Headers $grafanaHeaders -TimeoutSec 10
    $checks.grafanaDatasourceProvisioned = @($datasources | Where-Object { $_.uid -eq "ainote-prometheus" }).Count -eq 1
    $dashboards = Invoke-RestMethod -Uri "http://127.0.0.1:$grafanaPort/api/search?query=AiNote" -Headers $grafanaHeaders -TimeoutSec 10
    $checks.grafanaDashboardProvisioned = @($dashboards | Where-Object { $_.uid -eq "ainote-operations" }).Count -eq 1
    if (-not ($checks.grafanaDatasourceProvisioned -and $checks.grafanaDashboardProvisioned)) {
        throw "Grafana provisioning verification failed"
    }

    $alert = [ordered]@{
        labels = [ordered]@{
            alertname = "AiNoteSyntheticCriticalDrill"
            severity = "critical"
            service = "ainote-backend"
            owner = "ainote-maintainers"
            drill_id = $drillAlertId
        }
        annotations = [ordered]@{
            summary = "Managed-local synthetic alert drill"
            description = "Synthetic notification delivery verification"
        }
        startsAt = $startedAt.AddMinutes(-1).ToString("o")
        endsAt = $startedAt.AddMinutes(10).ToString("o")
        generatorURL = "http://ainote.invalid/managed-local-drill"
    }
    $payload = ConvertTo-Json -InputObject @($alert) -Depth 8
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$alertmanagerPort/api/v2/alerts" -ContentType "application/json" -Body $payload -TimeoutSec 10 | Out-Null
    $checks.firingNotificationDelivered = Wait-ReceiverEvent -LogPath $receiverLog -DrillId $drillAlertId -Status "firing"
    if (-not $checks.firingNotificationDelivered) {
        throw "Synthetic firing notification was not delivered"
    }

    $alert.endsAt = (Get-Date).ToUniversalTime().AddSeconds(-1).ToString("o")
    $resolvedPayload = ConvertTo-Json -InputObject @($alert) -Depth 8
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$alertmanagerPort/api/v2/alerts" -ContentType "application/json" -Body $resolvedPayload -TimeoutSec 10 | Out-Null
    $checks.resolvedNotificationDelivered = Wait-ReceiverEvent -LogPath $receiverLog -DrillId $drillAlertId -Status "resolved" -TimeoutSeconds 75
    if (-not $checks.resolvedNotificationDelivered) {
        throw "Synthetic resolved notification was not delivered"
    }
} catch {
    $failure = $_.Exception.Message
} finally {
    $cleanupErrors = New-Object 'System.Collections.Generic.List[string]'
    foreach ($name in @($grafanaName, $prometheusName, $alertmanagerName)) {
        try { Remove-DrillContainer -Name $name } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    try { Remove-DrillNetwork -Name $networkName } catch { $cleanupErrors.Add($_.Exception.Message) }
    if ($receiverProcess -and -not $receiverProcess.HasExited) {
        Stop-Process -Id $receiverProcess.Id -Force -ErrorAction SilentlyContinue
        $receiverProcess.WaitForExit(10000) | Out-Null
    }

    $remainingContainers = @(& docker ps -a --filter "name=^/$runId" --format "{{.Names}}" 2>$null)
    $remainingNetworks = @(& docker network ls --filter "name=^$networkName$" --format "{{.Name}}" 2>$null)
    $cleanupPassed = $cleanupErrors.Count -eq 0 -and $remainingContainers.Count -eq 0 -and $remainingNetworks.Count -eq 0

    $report = [ordered]@{
        schemaVersion = "monitoring-incident-drill-v1"
        status = if ($null -eq $failure -and $cleanupPassed -and -not ($checks.Values -contains $false)) { "PASSED" } else { "FAILED" }
        qualityGatePassed = ($null -eq $failure -and $cleanupPassed -and -not ($checks.Values -contains $false))
        evidenceClass = "managed-local"
        productionProven = $false
        runId = $runId
        startedAt = $startedAt.ToString("o")
        completedAt = (Get-Date).ToUniversalTime().ToString("o")
        images = [ordered]@{
            prometheus = $prometheusImage
            alertmanager = $alertmanagerImage
            grafana = $grafanaImage
        }
        checks = $checks
        delivery = [ordered]@{
            firingNotifications = if ($checks.firingNotificationDelivered) { 1 } else { 0 }
            resolvedNotifications = if ($checks.resolvedNotificationDelivered) { 1 } else { 0 }
            receiver = "disposable local Java HTTP receiver"
        }
        cleanup = [ordered]@{
            passed = $cleanupPassed
            remainingContainers = @($remainingContainers)
            remainingNetworks = @($remainingNetworks)
            errors = @($cleanupErrors)
        }
        failure = $failure
        externalBoundaries = @(
            "No real paging provider was used",
            "No staffed on-call rotation was proven",
            "No production telemetry retention or production traffic was proven"
        )
    }

    $reportPath = Join-Path $resolvedReportDir "monitoring-incident-drill-report.json"
    $reportJson = $report | ConvertTo-Json -Depth 10
    if ($reportJson.Contains($monitoringToken) -or $reportJson.Contains($grafanaPassword)) {
        $checks.secretAbsentFromEvidence = $false
        $report.status = "FAILED"
        $report.qualityGatePassed = $false
        $report.failure = "Generated secret appeared in report"
        $reportJson = $report | ConvertTo-Json -Depth 10
    } else {
        $checks.secretAbsentFromEvidence = $true
        $report.checks = $checks
        $report.status = if ($null -eq $failure -and $cleanupPassed -and -not ($checks.Values -contains $false)) { "PASSED" } else { "FAILED" }
        $report.qualityGatePassed = ($report.status -eq "PASSED")
        $reportJson = $report | ConvertTo-Json -Depth 10
    }
    [IO.File]::WriteAllText($reportPath, $reportJson, (New-Object Text.UTF8Encoding($false)))

    $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
    if (-not $resolvedTempRoot.StartsWith($resolvedReportDir + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolvedTempRoot)).StartsWith("ainote-observability-", [StringComparison]::Ordinal)) {
        throw "Refusing to remove unsafe drill path: $resolvedTempRoot"
    }
    if (Test-Path -LiteralPath $resolvedTempRoot) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }

    [Array]::Clear([char[]]$monitoringToken, 0, $monitoringToken.Length)
    [Array]::Clear([char[]]$grafanaPassword, 0, $grafanaPassword.Length)

    Get-Content -LiteralPath $reportPath -Raw
    if (-not $report.qualityGatePassed) {
        exit 1
    }
}
