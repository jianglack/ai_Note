param(
  [ValidateSet("managed-local")]
  [string]$EnvironmentClass = "managed-local",
  [string]$JavaPath = "D:\Java\jdk17\bin\java.exe",
  [string]$K6Path = "C:\Program Files\k6\k6.exe",
  [string]$BackendJar = "",
  [string]$OutputRoot = "",
  [int]$UserCount = 64,
  [int]$BaselineRps = 5,
  [string]$WarmupDuration = "20s",
  [string]$BaselineDuration = "60s",
  [int[]]$CapacityRates = @(10, 25, 50, 100, 200),
  [string]$CapacityStageDuration = "45s",
  [string]$AcceptedCapacityDuration = "2m",
  [string]$SoakDuration = "30m",
  [int]$ExpectedPeakRps = 25,
  [double]$CapacityHeadroom = 0.70,
  [int]$SampleIntervalSeconds = 5,
  [int]$CooldownSeconds = 10,
  [double]$MinAvailability = 0.999,
  [double]$MaxP95Ms = 500,
  [double]$MaxP99Ms = 1000,
  [double]$AppendMaxP95Ms = 750,
  [double]$AppendMaxP99Ms = 1500,
  [double]$HistoryMaxP95Ms = 500,
  [double]$HistoryMaxP99Ms = 1000,
  [double]$MaxHeapUtilization = 0.85,
  [double]$MaxProcessCpuP95 = 0.85,
  [int]$MaxThreadDrift = 20,
  [switch]$SkipSoak
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($BackendJar)) {
  $BackendJar = Join-Path $RepoRoot "backend\target\backend-0.1.0.jar"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $RepoRoot "backend\target\sla-capacity"
}
$K6Script = Join-Path $RepoRoot "perf\ainote-sla.js"
$RunId = "sla-$([DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ'))-$PID"
$RunDir = Join-Path $OutputRoot $RunId
$TokenFile = Join-Path $env:TEMP "$RunId-tokens.txt"
$StartedAt = [DateTimeOffset]::UtcNow

$Failures = [System.Collections.Generic.List[string]]::new()
$Phases = [System.Collections.Generic.List[object]]::new()
$UserIds = [System.Collections.Generic.List[string]]::new()
$PostgresContainer = $null
$RedisContainer = $null
$BackendProcess = $null
$BaseUrl = $null
$ExpectedRows = 0L
$CapacityLowerBound = 0
$FirstFailingRate = $null
$RecommendedRate = 0
$CleanupPassed = $false
$RunnerException = $null

function Require-File {
  param([string]$Path)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Required file not found: $Path"
  }
}

function Invoke-Native {
  param([string]$FilePath, [string[]]$Arguments = @())
  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
  }
}

function Get-FreeTcpPort {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  $listener.Start()
  try {
    return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
  } finally {
    $listener.Stop()
  }
}

function New-RandomSecret {
  $bytes = New-Object byte[] 48
  $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $generator.GetBytes($bytes)
  } finally {
    $generator.Dispose()
  }
  return [Convert]::ToBase64String($bytes)
}

function Start-DisposableContainer {
  param(
    [string]$Name,
    [string]$Image,
    [string[]]$Arguments
  )
  $dockerArguments = @("run", "-d", "--name", $Name) + $Arguments + @($Image)
  Invoke-Native "docker" $dockerArguments
  return $Name
}

function Wait-Postgres {
  param([string]$Container)
  for ($i = 0; $i -lt 90; $i++) {
    & docker exec $Container pg_isready -U ainote -d ainote_sla *> $null
    if ($LASTEXITCODE -eq 0) { return }
    Start-Sleep -Seconds 1
  }
  throw "Disposable PostgreSQL did not become ready"
}

function Wait-Redis {
  param([string]$Container)
  for ($i = 0; $i -lt 60; $i++) {
    $pong = (& docker exec $Container redis-cli ping 2>$null)
    if ($LASTEXITCODE -eq 0 -and "$pong".Trim() -eq "PONG") { return }
    Start-Sleep -Seconds 1
  }
  throw "Disposable Redis did not become ready"
}

function Wait-Backend {
  param([string]$Url, [System.Diagnostics.Process]$Process)
  for ($i = 0; $i -lt 180; $i++) {
    if ($Process.HasExited) {
      throw "Isolated backend exited during startup with code $($Process.ExitCode)"
    }
    try {
      $health = Invoke-RestMethod -Uri "$Url/actuator/health" -TimeoutSec 3
      if ($health.status -eq "UP") { return }
    } catch {
      # Startup is still in progress.
    }
    Start-Sleep -Seconds 1
  }
  throw "Isolated backend did not become healthy within 180 seconds"
}

function Set-ProcessEnvironment {
  param([hashtable]$Values)
  $previous = @{}
  foreach ($name in $Values.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    [Environment]::SetEnvironmentVariable($name, [string]$Values[$name], "Process")
  }
  return $previous
}

function Restore-ProcessEnvironment {
  param([hashtable]$Previous)
  foreach ($name in $Previous.Keys) {
    [Environment]::SetEnvironmentVariable($name, $Previous[$name], "Process")
  }
}

function Register-TestUsers {
  param([string]$Url, [int]$Count, [string]$TokensPath)
  $tokens = [System.Collections.Generic.List[string]]::new()
  for ($i = 0; $i -lt $Count; $i++) {
    $username = "$RunId-u$i"
    $body = @{
      username = $username
      email = "$username@example.invalid"
      password = "SlaLocalOnly!$RunId-$i"
    } | ConvertTo-Json
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $csrfResponse = Invoke-RestMethod -Method Get -Uri "$Url/api/auth/csrf" `
      -WebSession $session -TimeoutSec 15
    if ([string]::IsNullOrWhiteSpace($csrfResponse.token)) {
      throw "CSRF bootstrap returned an incomplete response at index $i"
    }
    $registrationResponse = Invoke-WebRequest -UseBasicParsing -Method Post `
      -Uri "$Url/api/auth/register" -WebSession $session `
      -Headers @{ "X-XSRF-TOKEN" = [string]$csrfResponse.token } `
      -ContentType "application/json" -Body $body -TimeoutSec 15
    $response = $registrationResponse.Content | ConvertFrom-Json
    $authCookie = $session.Cookies.GetCookies([Uri]$Url)["AINOTE_AUTH"]
    if ($null -eq $authCookie -or [string]::IsNullOrWhiteSpace($authCookie.Value) -or
        [string]::IsNullOrWhiteSpace($response.userId)) {
      throw "Test user registration returned incomplete credentials at index $i"
    }
    $tokens.Add([string]$authCookie.Value)
    $UserIds.Add([string]$response.userId)
  }
  [System.IO.File]::WriteAllLines(
    $TokensPath,
    $tokens,
    [System.Text.UTF8Encoding]::new($false))
}

function Get-ActuatorValue {
  param([string]$Name, [string]$Token, [string[]]$Tags = @())
  $query = ""
  if ($Tags.Count -gt 0) {
    $encoded = $Tags | ForEach-Object { "tag=$([Uri]::EscapeDataString($_))" }
    $query = "?" + ($encoded -join "&")
  }
  $metric = Invoke-RestMethod -Uri "$BaseUrl/actuator/metrics/$Name$query" `
    -Headers @{ Authorization = "Bearer $Token" } -TimeoutSec 10
  $measurement = $metric.measurements | Where-Object { $_.statistic -eq "VALUE" } | Select-Object -First 1
  if ($null -eq $measurement) {
    throw "Metric $Name has no VALUE measurement"
  }
  return [double]$measurement.value
}

function Get-ResourceSample {
  param([string]$Token)
  $sample = [ordered]@{
    timestamp = [DateTimeOffset]::UtcNow.ToString("o")
    health = "UNKNOWN"
    uptimeSeconds = $null
    heapUsedBytes = $null
    heapMaxBytes = $null
    heapUtilization = $null
    liveThreads = $null
    processCpu = $null
    systemCpu = $null
    hikariActive = $null
    hikariPending = $null
    tomcatBusy = $null
    error = $null
  }
  try {
    $sample.health = (Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5).status
    $sample.uptimeSeconds = Get-ActuatorValue "process.uptime" $Token
    $sample.heapUsedBytes = Get-ActuatorValue "jvm.memory.used" $Token @("area:heap")
    $sample.heapMaxBytes = Get-ActuatorValue "jvm.memory.max" $Token @("area:heap")
    if ($sample.heapMaxBytes -gt 0) {
      $sample.heapUtilization = $sample.heapUsedBytes / $sample.heapMaxBytes
    }
    $sample.liveThreads = Get-ActuatorValue "jvm.threads.live" $Token
    $sample.processCpu = Get-ActuatorValue "process.cpu.usage" $Token
    $sample.systemCpu = Get-ActuatorValue "system.cpu.usage" $Token
    $sample.hikariActive = Get-ActuatorValue "hikaricp.connections.active" $Token
    $sample.hikariPending = Get-ActuatorValue "hikaricp.connections.pending" $Token
    $sample.tomcatBusy = Get-ActuatorValue "tomcat.threads.busy" $Token
  } catch {
    $sample.error = $_.Exception.GetType().Name
  }
  return [pscustomobject]$sample
}

function Get-Percentile {
  param([double[]]$Values, [double]$Percentile)
  if ($null -eq $Values -or $Values.Count -eq 0) { return $null }
  $sorted = @($Values | Sort-Object)
  $index = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
  $index = [Math]::Max(0, [Math]::Min($sorted.Count - 1, $index))
  return [double]$sorted[$index]
}

function Get-MetricValues {
  param([object]$Summary, [string]$Name)
  $property = $Summary.metrics.PSObject.Properties[$Name]
  if ($null -eq $property) { return $null }
  $metric = $property.Value
  $valuesProperty = $metric.PSObject.Properties["values"]
  if ($null -ne $valuesProperty) { return $valuesProperty.Value }
  return $metric
}

function Get-ValueOrDefault {
  param([object]$Object, [string]$Name, [double]$Default = 0)
  if ($null -eq $Object) { return $Default }
  $property = $Object.PSObject.Properties[$Name]
  if ($null -eq $property -or $null -eq $property.Value) { return $Default }
  return [double]$property.Value
}

function Evaluate-Phase {
  param([System.Collections.IDictionary]$Phase)
  $phaseFailures = [System.Collections.Generic.List[string]]::new()
  if ($Phase.exitCode -ne 0) { $phaseFailures.Add("k6_exit_code_$($Phase.exitCode)") }
  if ($Phase.availability -lt $MinAvailability) { $phaseFailures.Add("availability_below_threshold") }
  if ($Phase.checkRate -lt $MinAvailability) { $phaseFailures.Add("check_rate_below_threshold") }
  if ($Phase.p95Ms -gt $MaxP95Ms) { $phaseFailures.Add("mixed_p95_exceeded") }
  if ($Phase.p99Ms -gt $MaxP99Ms) { $phaseFailures.Add("mixed_p99_exceeded") }
  if ($Phase.appendP95Ms -gt $AppendMaxP95Ms) { $phaseFailures.Add("append_p95_exceeded") }
  if ($Phase.appendP99Ms -gt $AppendMaxP99Ms) { $phaseFailures.Add("append_p99_exceeded") }
  if ($Phase.historyP95Ms -gt $HistoryMaxP95Ms) { $phaseFailures.Add("history_p95_exceeded") }
  if ($Phase.historyP99Ms -gt $HistoryMaxP99Ms) { $phaseFailures.Add("history_p99_exceeded") }
  if ($Phase.droppedIterations -ne 0) { $phaseFailures.Add("dropped_iterations_nonzero") }
  if ([Math]::Abs($Phase.appendRatio - 0.30) -gt 0.01) { $phaseFailures.Add("append_ratio_out_of_range") }
  if ([Math]::Abs($Phase.historyRatio - 0.30) -gt 0.01) { $phaseFailures.Add("history_ratio_out_of_range") }
  if ([Math]::Abs($Phase.memoryListRatio - 0.20) -gt 0.01) { $phaseFailures.Add("memory_list_ratio_out_of_range") }
  if ([Math]::Abs($Phase.notesRatio - 0.20) -gt 0.01) { $phaseFailures.Add("notes_ratio_out_of_range") }
  if ($Phase.resourceSamples -lt 2) { $phaseFailures.Add("insufficient_resource_samples") }
  if ($Phase.resourceSampleErrors -ne 0) { $phaseFailures.Add("resource_sample_error") }
  if ($Phase.healthFailures -ne 0) { $phaseFailures.Add("health_probe_failure") }
  if (-not $Phase.uptimeMonotonic) { $phaseFailures.Add("process_restart_detected") }
  if ($Phase.maxHeapUtilization -ge $MaxHeapUtilization) { $phaseFailures.Add("heap_utilization_exceeded") }
  if ($Phase.processCpuP95 -gt $MaxProcessCpuP95) { $phaseFailures.Add("process_cpu_p95_exceeded") }
  if ($Phase.maxHikariPending -gt 0) { $phaseFailures.Add("hikari_pending_connections") }
  if ($Phase.enforceThreadDrift -and $Phase.threadDrift -gt $MaxThreadDrift) {
    $phaseFailures.Add("thread_drift_exceeded")
  }
  $Phase.failures = @($phaseFailures)
  $Phase.gatePassed = $phaseFailures.Count -eq 0
}

function Invoke-K6Phase {
  param(
    [string]$Name,
    [int]$TargetRps,
    [string]$Duration,
    [bool]$HardGate,
    [bool]$EnforceThreadDrift,
    [string]$Token
  )
  $summaryPath = Join-Path $RunDir "$Name-summary.json"
  $stdoutPath = Join-Path $RunDir "$Name-k6.out.log"
  $stderrPath = Join-Path $RunDir "$Name-k6.err.log"
  $resourcePath = Join-Path $RunDir "$Name-resources.json"
  $samples = [System.Collections.Generic.List[object]]::new()
  $before = Get-ResourceSample $Token
  $samples.Add($before)

  $environment = @{
    BASE_URL = $BaseUrl
    AUTH_TOKENS_FILE = $TokenFile
    SLA_RUN_ID = $RunId
    TARGET_RPS = $TargetRps
    DURATION = $Duration
    HARD_GATE = $HardGate.ToString().ToLowerInvariant()
    MAX_VUS = [Math]::Max(40, $TargetRps * 4)
    PREALLOCATED_VUS = [Math]::Max(20, $TargetRps * 2)
    SLA_MIN_AVAILABILITY = $MinAvailability
    SLA_MAX_P95_MS = $MaxP95Ms
    SLA_MAX_P99_MS = $MaxP99Ms
    SLA_APPEND_MAX_P95_MS = $AppendMaxP95Ms
    SLA_APPEND_MAX_P99_MS = $AppendMaxP99Ms
    SLA_HISTORY_MAX_P95_MS = $HistoryMaxP95Ms
    SLA_HISTORY_MAX_P99_MS = $HistoryMaxP99Ms
  }
  $previous = Set-ProcessEnvironment $environment
  try {
    $process = Start-Process -FilePath $K6Path `
      -ArgumentList @("run", "--summary-export", $summaryPath, $K6Script) `
      -WorkingDirectory $RepoRoot -WindowStyle Hidden `
      -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
  } finally {
    Restore-ProcessEnvironment $previous
  }

  while (-not $process.HasExited) {
    Start-Sleep -Seconds $SampleIntervalSeconds
    $process.Refresh()
    $samples.Add((Get-ResourceSample $Token))
  }
  $process.WaitForExit()
  $process.Refresh()
  $exitCode = [int]$process.ExitCode
  Start-Sleep -Seconds $CooldownSeconds
  $after = Get-ResourceSample $Token
  $samples.Add($after)
  [System.IO.File]::WriteAllText(
    $resourcePath,
    ($samples | ConvertTo-Json -Depth 6),
    [System.Text.UTF8Encoding]::new($false))

  if (-not (Test-Path -LiteralPath $summaryPath)) {
    throw "k6 did not create summary for phase $Name"
  }
  $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
  $request = Get-MetricValues $summary "sla_request_success"
  $checks = Get-MetricValues $summary "checks"
  $latency = Get-MetricValues $summary "sla_request_latency"
  $append = Get-MetricValues $summary "sla_append_latency"
  $history = Get-MetricValues $summary "sla_history_latency"
  $appendCounter = Get-MetricValues $summary "sla_append_success"
  $appendOperations = Get-MetricValues $summary "sla_append_operations"
  $historyOperations = Get-MetricValues $summary "sla_history_operations"
  $memoryListOperations = Get-MetricValues $summary "sla_memory_list_operations"
  $notesOperations = Get-MetricValues $summary "sla_notes_operations"
  $iterations = Get-MetricValues $summary "iterations"
  $dropped = Get-MetricValues $summary "dropped_iterations"

  $validSamples = @($samples | Where-Object { [string]::IsNullOrWhiteSpace($_.error) })
  $uptimes = @($validSamples | ForEach-Object { [double]$_.uptimeSeconds })
  $uptimeMonotonic = $true
  for ($i = 1; $i -lt $uptimes.Count; $i++) {
    if ($uptimes[$i] -lt $uptimes[$i - 1]) { $uptimeMonotonic = $false; break }
  }
  $heapRatios = @($validSamples | ForEach-Object { [double]$_.heapUtilization })
  $processCpus = @($validSamples | ForEach-Object { [double]$_.processCpu })
  $pending = @($validSamples | ForEach-Object { [double]$_.hikariPending })
  $threadsBefore = if ($null -eq $before.liveThreads) { 0 } else { [double]$before.liveThreads }
  $threadsAfter = if ($null -eq $after.liveThreads) { 0 } else { [double]$after.liveThreads }

  $totalRequests = [long](Get-ValueOrDefault $iterations "count")
  $appendOperationCount = [long](Get-ValueOrDefault $appendOperations "count")
  $historyOperationCount = [long](Get-ValueOrDefault $historyOperations "count")
  $memoryListOperationCount = [long](Get-ValueOrDefault $memoryListOperations "count")
  $notesOperationCount = [long](Get-ValueOrDefault $notesOperations "count")
  $phase = [ordered]@{
    name = $Name
    targetRps = $TargetRps
    configuredDuration = $Duration
    hardGate = $HardGate
    enforceThreadDrift = $EnforceThreadDrift
    exitCode = $exitCode
    requests = $totalRequests
    actualRps = Get-ValueOrDefault $iterations "rate"
    availability = Get-ValueOrDefault $request "value"
    checkRate = Get-ValueOrDefault $checks "value"
    p50Ms = Get-ValueOrDefault $latency "med"
    p95Ms = Get-ValueOrDefault $latency "p(95)"
    p99Ms = Get-ValueOrDefault $latency "p(99)"
    maxMs = Get-ValueOrDefault $latency "max"
    appendP95Ms = Get-ValueOrDefault $append "p(95)"
    appendP99Ms = Get-ValueOrDefault $append "p(99)"
    historyP95Ms = Get-ValueOrDefault $history "p(95)"
    historyP99Ms = Get-ValueOrDefault $history "p(99)"
    successfulAppends = [long](Get-ValueOrDefault $appendCounter "count")
    appendOperations = $appendOperationCount
    historyOperations = $historyOperationCount
    memoryListOperations = $memoryListOperationCount
    notesOperations = $notesOperationCount
    appendRatio = if ($totalRequests -eq 0) { 0 } else { $appendOperationCount / $totalRequests }
    historyRatio = if ($totalRequests -eq 0) { 0 } else { $historyOperationCount / $totalRequests }
    memoryListRatio = if ($totalRequests -eq 0) { 0 } else { $memoryListOperationCount / $totalRequests }
    notesRatio = if ($totalRequests -eq 0) { 0 } else { $notesOperationCount / $totalRequests }
    droppedIterations = [long](Get-ValueOrDefault $dropped "count")
    resourceSamples = $samples.Count
    resourceSampleErrors = @($samples | Where-Object { -not [string]::IsNullOrWhiteSpace($_.error) }).Count
    healthFailures = @($samples | Where-Object { $_.health -ne "UP" }).Count
    uptimeMonotonic = $uptimeMonotonic
    maxHeapUtilization = if ($heapRatios.Count -eq 0) { 1.0 } else { ($heapRatios | Measure-Object -Maximum).Maximum }
    processCpuP95 = if ($processCpus.Count -eq 0) { 1.0 } else { Get-Percentile $processCpus 95 }
    maxHikariPending = if ($pending.Count -eq 0) { 1.0 } else { ($pending | Measure-Object -Maximum).Maximum }
    threadDrift = $threadsAfter - $threadsBefore
    summarySha256 = (Get-FileHash -LiteralPath $summaryPath -Algorithm SHA256).Hash
    resourceSha256 = (Get-FileHash -LiteralPath $resourcePath -Algorithm SHA256).Hash
    gatePassed = $false
    failures = @()
  }
  Evaluate-Phase $phase
  return $phase
}

function Invoke-PsqlRow {
  param([string]$Sql)
  $output = & docker exec $PostgresContainer psql -U ainote -d ainote_sla -At -F "|" -c $Sql
  if ($LASTEXITCODE -ne 0) { throw "PostgreSQL audit query failed" }
  return "$output".Trim()
}

function Get-IntegrityEvidence {
  param([long]$Expected)
  if ($UserIds.Count -eq 0) { throw "No test user IDs are available for integrity audit" }
  foreach ($id in $UserIds) {
    if ($id -notmatch '^[0-9a-fA-F-]{36}$') { throw "Unsafe test user ID" }
  }
  $ids = ($UserIds | ForEach-Object { "'$_'" }) -join ","
  $sql = @"
WITH selected AS (
  SELECT user_id, sequence_number FROM user_memories WHERE user_id IN ($ids)
), per_user AS (
  SELECT user_id, COUNT(*) AS rows, MIN(sequence_number) AS min_seq,
         MAX(sequence_number) AS max_seq, COUNT(DISTINCT sequence_number) AS distinct_seq
  FROM selected GROUP BY user_id
), head_mismatch AS (
  SELECT COUNT(*) AS count
  FROM per_user p LEFT JOIN chat_memory_heads h ON h.user_id = p.user_id
  WHERE h.user_id IS NULL OR h.next_sequence_number <> p.rows
)
SELECT
  (SELECT COUNT(*) FROM selected),
  (SELECT COUNT(*) FROM selected WHERE sequence_number IS NULL),
  COALESCE((SELECT SUM(rows - distinct_seq) FROM per_user), 0),
  (SELECT COUNT(*) FROM per_user WHERE min_seq <> 0 OR max_seq <> rows - 1 OR distinct_seq <> rows),
  (SELECT count FROM head_mismatch);
"@
  $parts = (Invoke-PsqlRow $sql) -split '\|'
  if ($parts.Count -ne 5) { throw "Unexpected integrity audit output" }
  $evidence = [ordered]@{
    expectedRows = $Expected
    persistedRows = [long]$parts[0]
    nullSequences = [long]$parts[1]
    duplicateSequences = [long]$parts[2]
    usersWithSequenceGaps = [long]$parts[3]
    headMismatches = [long]$parts[4]
    passed = $false
    failures = @()
  }
  $integrityFailures = [System.Collections.Generic.List[string]]::new()
  if ($evidence.persistedRows -ne $Expected) { $integrityFailures.Add("persisted_row_count_mismatch") }
  if ($evidence.nullSequences -ne 0) { $integrityFailures.Add("null_sequence_detected") }
  if ($evidence.duplicateSequences -ne 0) { $integrityFailures.Add("duplicate_sequence_detected") }
  if ($evidence.usersWithSequenceGaps -ne 0) { $integrityFailures.Add("sequence_gap_detected") }
  if ($evidence.headMismatches -ne 0) { $integrityFailures.Add("head_sequence_mismatch") }
  $evidence.failures = @($integrityFailures)
  $evidence.passed = $integrityFailures.Count -eq 0
  return [pscustomobject]$evidence
}

function Add-ScoredPhase {
  param([System.Collections.IDictionary]$Phase, [bool]$AffectsOverallGate)
  $script:ExpectedRows += 2L * [long]$Phase.successfulAppends
  $integrity = Get-IntegrityEvidence $script:ExpectedRows
  $Phase["integrity"] = $integrity
  if (-not $integrity.passed) {
    $Phase.gatePassed = $false
    $Phase.failures = @($Phase.failures) + @($integrity.failures)
  }
  $Phases.Add([pscustomobject]$Phase)
  if ($AffectsOverallGate -and -not $Phase.gatePassed) {
    foreach ($failure in $Phase.failures) { $Failures.Add("$($Phase.name):$failure") }
  }
}

function Stop-BackendProcess {
  param([System.Diagnostics.Process]$Process)
  if ($null -eq $Process -or $Process.HasExited) { return }
  try {
    Stop-Process -Id $Process.Id -ErrorAction Stop
    if (-not $Process.WaitForExit(15000)) {
      Stop-Process -Id $Process.Id -Force -ErrorAction Stop
      $Process.WaitForExit(10000) | Out-Null
    }
  } catch {
    $Failures.Add("cleanup_backend_process_failed")
  }
}

function Remove-Container {
  param([string]$Name)
  if ([string]::IsNullOrWhiteSpace($Name)) { return }
  & docker rm -f $Name *> $null
  if ($LASTEXITCODE -ne 0) { $Failures.Add("cleanup_container_failed:$Name") }
}

function Test-ContainerAbsent {
  param([string]$Name)
  if ([string]::IsNullOrWhiteSpace($Name)) { return $true }
  $id = & docker ps -aq --filter "name=^/$Name$"
  return [string]::IsNullOrWhiteSpace("$id")
}

New-Item -ItemType Directory -Path $RunDir -Force | Out-Null

try {
  Require-File $JavaPath
  Require-File $K6Path
  Require-File $BackendJar
  Require-File $K6Script
  Invoke-Native "docker" @("version", "--format", "{{.Server.Version}}")

  $PostgresContainer = "$RunId-pg"
  $RedisContainer = "$RunId-redis"
  Start-DisposableContainer $PostgresContainer "pgvector/pgvector:pg15" @(
    "-p", "127.0.0.1::5432",
    "-e", "POSTGRES_USER=ainote",
    "-e", "POSTGRES_PASSWORD=ainote_sla_password",
    "-e", "POSTGRES_DB=ainote_sla"
  ) | Out-Null
  Start-DisposableContainer $RedisContainer "redis:7-alpine" @(
    "-p", "127.0.0.1::6379"
  ) | Out-Null
  Wait-Postgres $PostgresContainer
  Wait-Redis $RedisContainer

  $postgresPort = ((& docker port $PostgresContainer "5432/tcp") -split ":")[-1].Trim()
  $redisPort = ((& docker port $RedisContainer "6379/tcp") -split ":")[-1].Trim()
  if (-not $postgresPort -or -not $redisPort) { throw "Could not resolve disposable container ports" }

  $backendPort = Get-FreeTcpPort
  $BaseUrl = "http://127.0.0.1:$backendPort"
  $backendStdout = Join-Path $RunDir "backend.out.log"
  $backendStderr = Join-Path $RunDir "backend.err.log"
  $backendEnvironment = @{
    SPRING_PROFILES_ACTIVE = "prod"
    SERVER_PORT = $backendPort
    SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$postgresPort/ainote_sla?stringtype=unspecified"
    SPRING_DATASOURCE_USERNAME = "ainote"
    SPRING_DATASOURCE_PASSWORD = "ainote_sla_password"
    SPRING_DATA_REDIS_HOST = "127.0.0.1"
    SPRING_DATA_REDIS_PORT = $redisPort
    NEO4J_ENABLED = "false"
    DB_POOL_MAX_SIZE = "30"
    DB_POOL_MIN_IDLE = "5"
    JWT_SECRET = New-RandomSecret
    AUTH_COOKIE_SECURE = "false"
    MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,metrics,prometheus"
    MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS = "always"
    SERVER_TOMCAT_MBEANREGISTRY_ENABLED = "true"
    MEMORY_CHAT_HISTORY_COMPACTION_ENABLED = "false"
    DEEPSEEK_API_KEY = "sla-local-no-external-model-call"
  }
  $previous = Set-ProcessEnvironment $backendEnvironment
  try {
    $BackendProcess = Start-Process -FilePath $JavaPath `
      -ArgumentList @("-Xms512m", "-Xmx1024m", "-jar", $BackendJar) `
      -WorkingDirectory (Split-Path -Parent $BackendJar) -WindowStyle Hidden `
      -RedirectStandardOutput $backendStdout -RedirectStandardError $backendStderr -PassThru
  } finally {
    Restore-ProcessEnvironment $previous
  }
  Wait-Backend $BaseUrl $BackendProcess
  Register-TestUsers $BaseUrl $UserCount $TokenFile
  $monitorToken = (Get-Content -LiteralPath $TokenFile -TotalCount 1).Trim()

  $warmup = Invoke-K6Phase "warmup" $BaselineRps $WarmupDuration $false $false $monitorToken
  Add-ScoredPhase $warmup $false

  $baseline = Invoke-K6Phase "baseline" $BaselineRps $BaselineDuration $true $false $monitorToken
  Add-ScoredPhase $baseline $true

  foreach ($rate in $CapacityRates) {
    $phase = Invoke-K6Phase "capacity-$rate" $rate $CapacityStageDuration $false $false $monitorToken
    Add-ScoredPhase $phase $false
    if ($phase.gatePassed) {
      $CapacityLowerBound = $rate
    } else {
      $FirstFailingRate = $rate
      break
    }
  }

  if ($CapacityLowerBound -le 0) {
    $Failures.Add("capacity:no_passing_stage")
  } else {
    $headroomRate = [Math]::Max(1, [Math]::Floor($CapacityLowerBound * $CapacityHeadroom))
    $RecommendedRate = [Math]::Min($ExpectedPeakRps, $headroomRate)
    if ($headroomRate -lt $ExpectedPeakRps) {
      $Failures.Add("capacity:expected_peak_lacks_headroom")
    }
  }

  if ($RecommendedRate -gt 0) {
    $accepted = Invoke-K6Phase "accepted-capacity" $RecommendedRate $AcceptedCapacityDuration $true $false $monitorToken
    Add-ScoredPhase $accepted $true
    if (-not $SkipSoak) {
      $soak = Invoke-K6Phase "release-soak" $RecommendedRate $SoakDuration $true $true $monitorToken
      Add-ScoredPhase $soak $true
    }
  }
} catch {
  $RunnerException = $_.Exception.GetType().Name + ": " + $_.Exception.Message
  $Failures.Add("runner_exception:$($RunnerException)")
} finally {
  if (Test-Path -LiteralPath $TokenFile) {
    Remove-Item -LiteralPath $TokenFile -Force -ErrorAction SilentlyContinue
  }
  Stop-BackendProcess $BackendProcess
  Remove-Container $RedisContainer
  Remove-Container $PostgresContainer
  $CleanupPassed = (Test-ContainerAbsent $RedisContainer) -and
                   (Test-ContainerAbsent $PostgresContainer) -and
                   ($null -eq $BackendProcess -or $BackendProcess.HasExited)
  if (-not $CleanupPassed) { $Failures.Add("cleanup_verification_failed") }
}

$gitCommit = (& git -C $RepoRoot rev-parse HEAD 2>$null)
$gitDirty = -not [string]::IsNullOrWhiteSpace((& git -C $RepoRoot status --porcelain 2>$null) -join "")
$os = Get-CimInstance Win32_OperatingSystem
$computer = Get-CimInstance Win32_ComputerSystem
$qualityGatePassed = $Failures.Count -eq 0
$report = [ordered]@{
  schemaVersion = "ainote-production-sla-capacity-v1"
  status = if ($qualityGatePassed) { "PASSED" } else { "FAILED" }
  qualityGatePassed = $qualityGatePassed
  evidenceClass = $EnvironmentClass
  productionProven = $false
  runId = $RunId
  startedAt = $StartedAt.ToString("o")
  finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
  source = [ordered]@{
    gitCommit = "$gitCommit".Trim()
    dirtyWorktree = $gitDirty
  }
  topology = [ordered]@{
    backendInstances = 1
    springProfile = "prod"
    java = $JavaPath
    jvmInitialHeapMb = 512
    jvmMaxHeapMb = 1024
    hikariMaximumPoolSize = 30
    database = "disposable pgvector/pgvector:pg15"
    cache = "disposable redis:7-alpine"
    neo4jEnabled = $false
    compactionEnabled = $false
    logicalProcessors = [int]$computer.NumberOfLogicalProcessors
    totalMemoryBytes = [long]$os.TotalVisibleMemorySize * 1024L
  }
  workload = [ordered]@{
    users = $UserCount
    appendPercent = 30
    historyPercent = 30
    memoryListPercent = 20
    notesPercent = 20
    expectedPeakRps = $ExpectedPeakRps
    releaseSoakDuration = if ($SkipSoak) { "skipped" } else { $SoakDuration }
  }
  slos = [ordered]@{
    minAvailability = $MinAvailability
    maxP95Ms = $MaxP95Ms
    maxP99Ms = $MaxP99Ms
    appendMaxP95Ms = $AppendMaxP95Ms
    appendMaxP99Ms = $AppendMaxP99Ms
    historyMaxP95Ms = $HistoryMaxP95Ms
    historyMaxP99Ms = $HistoryMaxP99Ms
    maxHeapUtilization = $MaxHeapUtilization
    maxProcessCpuP95 = $MaxProcessCpuP95
    maxThreadDrift = $MaxThreadDrift
    maxHikariPending = 0
  }
  capacity = [ordered]@{
    testedRates = @($CapacityRates)
    demonstratedLowerBoundRps = $CapacityLowerBound
    firstFailingRateRps = $FirstFailingRate
    headroomRatio = $CapacityHeadroom
    recommendedOperatingRateRps = $RecommendedRate
    ceilingFound = $null -ne $FirstFailingRate
  }
  phases = @($Phases)
  cleanup = [ordered]@{
    tokenFileDeleted = -not (Test-Path -LiteralPath $TokenFile)
    backendStopped = $null -eq $BackendProcess -or $BackendProcess.HasExited
    containersRemoved = (Test-ContainerAbsent $RedisContainer) -and (Test-ContainerAbsent $PostgresContainer)
    passed = $CleanupPassed
  }
  runnerException = $RunnerException
  failures = @($Failures)
}

$reportPath = Join-Path $RunDir "sla-capacity-report.json"
$latestPath = Join-Path $OutputRoot "latest.json"
$json = $report | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText($reportPath, $json, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($latestPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Output "report=$reportPath"
Write-Output "status=$($report.status)"
Write-Output "capacityLowerBoundRps=$CapacityLowerBound"
Write-Output "recommendedOperatingRateRps=$RecommendedRate"
Write-Output "cleanupPassed=$CleanupPassed"

if (-not $qualityGatePassed) { exit 1 }
