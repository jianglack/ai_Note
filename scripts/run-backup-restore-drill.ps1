param(
  [string]$JavaPath = "D:\Java\jdk17\bin\java.exe",
  [string]$BackendJar = "",
  [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "lib\AiNoteBackup.Common.ps1")

if ([string]::IsNullOrWhiteSpace($BackendJar)) {
  $BackendJar = Join-Path $RepoRoot "backend\target\backend-0.1.0.jar"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $RepoRoot "backend\target\backup-restore-drill"
}
[IO.Directory]::CreateDirectory($OutputRoot) | Out-Null

$startedAt = [DateTimeOffset]::UtcNow
$runId = "ainote-dr-$($startedAt.ToString('yyyyMMddTHHmmssZ'))-$PID"
$runDirectory = Join-Path $OutputRoot $runId
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$sourcePostgres = "$runId-source-pg"
$sourceRedis = "$runId-source-redis"
$targetPostgres = "$runId-target-pg"
$corruptTargetPostgres = "$runId-corrupt-pg"
$containers = @($sourcePostgres, $sourceRedis, $targetPostgres, $corruptTargetPostgres)
$backendProcess = $null
$backupPackage = $null
$restoreReportPath = $null
$sourceDestroyedAt = $null
$restoreStartedAt = $null
$restoreFinishedAt = $null
$corruptionRejected = $false
$corruptTargetUntouched = $false
$nonEmptyTargetRejected = $false
$representativeChecks = [ordered]@{}
$failures = [System.Collections.Generic.List[string]]::new()
$cleanupPassed = $false
$keyDestroyed = $false
$previousBackupKey = [Environment]::GetEnvironmentVariable("AINOTE_BACKUP_KEY_BASE64", "Process")

function Get-FreeTcpPort {
  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
  $listener.Start()
  try { return ([Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function New-RandomBase64 {
  param([int]$Length)
  $bytes = New-Object byte[] $Length
  $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
  return [Convert]::ToBase64String($bytes)
}

function Wait-Postgres {
  param([string]$Container, [string]$Database, [string]$User)
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    & docker exec $Container pg_isready -U $User -d $Database *> $null
    if ($LASTEXITCODE -eq 0) { return }
    Start-Sleep -Seconds 1
  }
  throw "Disposable PostgreSQL did not become ready: $Container"
}

function Wait-Redis {
  param([string]$Container)
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $pong = & docker exec $Container redis-cli ping 2>$null
    if ($LASTEXITCODE -eq 0 -and "$pong".Trim() -eq "PONG") { return }
    Start-Sleep -Seconds 1
  }
  throw "Disposable Redis did not become ready: $Container"
}

function Wait-Backend {
  param([string]$Url, [Diagnostics.Process]$Process)
  for ($attempt = 0; $attempt -lt 180; $attempt++) {
    if ($Process.HasExited) { throw "Migration backend exited with code $($Process.ExitCode)" }
    try {
      $health = Invoke-RestMethod -Uri "$Url/actuator/health" -TimeoutSec 3
      if ($health.status -eq "UP") { return }
    } catch {
      # Startup is still in progress.
    }
    Start-Sleep -Seconds 1
  }
  throw "Migration backend did not become healthy"
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

function Stop-Backend {
  param([Diagnostics.Process]$Process)
  if ($null -eq $Process -or $Process.HasExited) { return }
  Stop-Process -Id $Process.Id -Force
  $Process.WaitForExit(15000) | Out-Null
}

function Test-ContainerAbsent {
  param([string]$Container)
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "SilentlyContinue"
  try {
    & docker inspect $Container *> $null
    return $LASTEXITCODE -ne 0
  } finally {
    $ErrorActionPreference = $previousPreference
  }
}

function Remove-DrillContainer {
  param([string]$Container)
  if ($Container -notmatch '^ainote-dr-') { throw "Refusing to remove non-drill container: $Container" }
  & docker rm -f $Container *> $null
}

function Invoke-PowerShellFile {
  param([string]$Path, [string[]]$Arguments)
  $powershell = Join-Path $PSHOME "powershell.exe"
  $output = @(& $powershell -NoProfile -ExecutionPolicy Bypass -File $Path @Arguments 2>&1)
  return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Get-OutputValue {
  param([object[]]$Output, [string]$Name)
  $line = $Output | Where-Object { "$_" -like "$Name=*" } | Select-Object -Last 1
  if ($null -eq $line) { throw "Expected script output is missing: $Name" }
  return "$line".Substring($Name.Length + 1)
}

function Start-PostgresContainer {
  param([string]$Name, [string]$Password)
  Invoke-AiNoteNative docker @(
    "run", "-d", "--name", $Name,
    "-e", "POSTGRES_USER=ainote",
    "-e", "POSTGRES_PASSWORD=$Password",
    "-e", "POSTGRES_DB=ainote_dr",
    "-p", "127.0.0.1::5432",
    "pgvector/pgvector:pg15") | Out-Null
  Wait-Postgres $Name "ainote_dr" "ainote"
}

function Seed-RepresentativeData {
  param([string]$Container)
  $sql = @'
INSERT INTO users(id, username, email, password_hash, created_at)
VALUES ('dr-user-1', 'dr-user', 'dr-user@example.invalid', '$2a$10$managed.local.fixture.only', '2026-07-11 01:00:00');

INSERT INTO folders(id, name, created_at, updated_at, user_id, color)
VALUES ('dr-folder-1', 'Disaster recovery', '2026-07-11 01:01:00', '2026-07-11 01:01:00', 'dr-user-1', '#336699');
INSERT INTO tags(id, name, user_id) VALUES ('dr-tag-1', 'recovery', 'dr-user-1');
INSERT INTO notes(id, title, content, user_id, folder_id, created_at, updated_at, pinned, starred, archived, version)
VALUES ('dr-note-1', 'Recovery fixture', 'PostgreSQL is authoritative. See [[Recovery fixture]].',
        'dr-user-1', 'dr-folder-1', '2026-07-11 01:02:00', '2026-07-11 01:02:00', true, false, false, 3);
INSERT INTO note_tags(note_id, tag_id) VALUES ('dr-note-1', 'dr-tag-1');
INSERT INTO note_versions(id, note_id, content, created_at)
VALUES ('dr-version-1', 'dr-note-1', 'Recovery fixture version one', '2026-07-11 01:02:30');
INSERT INTO note_media(id, note_id, user_id, media_type, filename, mime_type, data_base64, ocr_text,
                       metadata, file_size_bytes, created_at, updated_at)
VALUES ('dr-media-1', 'dr-note-1', 'dr-user-1', 'image', 'fixture.txt', 'text/plain',
        encode(convert_to('AiNote DR media fixture', 'UTF8'), 'base64'), 'AiNote DR media fixture',
        jsonb_build_object('fixture', true), 22, '2026-07-11 01:03:00', '2026-07-11 01:03:00');

INSERT INTO langchain4j_embeddings(embedding_id, embedding, text, metadata)
SELECT '11111111-1111-1111-1111-111111111111'::uuid,
       ('[' || array_to_string(array_fill('0.001'::text, ARRAY[1024]), ',') || ']')::vector,
       'Recovery fixture embedding', json_build_object('userId', 'dr-user-1', 'noteId', 'dr-note-1');

INSERT INTO semantic_memories(user_id, category, content, confidence, source, created_at, updated_at,
                              embedding, memory_type, scope, status, source_trace_id, content_hash)
SELECT 'dr-user-1', 'preference', 'Use Chinese and put the conclusion first', 0.95, 'dr_fixture',
       '2026-07-11 01:04:00', '2026-07-11 01:04:00',
       ('[' || array_to_string(array_fill('0.002'::text, ARRAY[1024]), ',') || ']')::vector,
       'preference', 'user', 'active', 'dr-trace-1', 'dr-memory-content-hash';
INSERT INTO episodic_memories(user_id, session_summary, key_topics, message_count, created_at,
                              session_id, thread_id, status, source_message_range)
VALUES ('dr-user-1', 'A disaster recovery drill was planned', 'backup,recovery', 2,
        '2026-07-11 01:05:00', 'dr-session-1', 'dr-thread-1', 'active', 'dr-user-1:0-1');
INSERT INTO user_memories(user_id, message_type, content, created_at, sequence_number)
VALUES ('dr-user-1', 'USER', 'Please remember the recovery procedure', '2026-07-11 01:05:10', 0),
       ('dr-user-1', 'AI', 'The procedure is recorded', '2026-07-11 01:05:11', 1);
INSERT INTO chat_memory_heads(user_id, next_sequence_number, last_compaction_enqueued_sequence, version, updated_at)
VALUES ('dr-user-1', 2, -1, 1, '2026-07-11 01:05:12')
ON CONFLICT (user_id) DO UPDATE SET next_sequence_number=EXCLUDED.next_sequence_number, version=EXCLUDED.version;
INSERT INTO chat_memory_compaction_jobs(user_id, from_sequence, to_sequence, user_message_count, status,
                                        attempts, created_at, updated_at)
VALUES ('dr-user-1', 0, 1, 1, 'pending', 0, '2026-07-11 01:05:13', '2026-07-11 01:05:13');

INSERT INTO note_concepts(note_id, user_id, concept, category, confidence, extracted_at)
VALUES ('dr-note-1', 'dr-user-1', 'disaster recovery', 'topic', 0.93, '2026-07-11 01:06:00');
INSERT INTO memory_events(user_id, memory_id, event_type, actor, reason, trace_id, created_at)
SELECT 'dr-user-1', id, 'memory_created', 'policy', 'dr_fixture', 'dr-trace-1', '2026-07-11 01:06:10'
FROM semantic_memories WHERE user_id='dr-user-1' AND content_hash='dr-memory-content-hash';
INSERT INTO memory_review_cases(user_id, memory_id, feedback_type, user_comment, status, created_at, updated_at)
SELECT 'dr-user-1', id, 'wrong_memory', 'fixture review', 'pending_review',
       '2026-07-11 01:06:20', '2026-07-11 01:06:20'
FROM semantic_memories WHERE user_id='dr-user-1' AND content_hash='dr-memory-content-hash';
INSERT INTO admin_access_audits(user_id, action, decision, reason, created_at)
VALUES ('dr-user-1', 'memory_export', 'allowed', 'dr_fixture', '2026-07-11 01:06:30');
'@
  Invoke-AiNotePsql $Container "ainote_dr" "ainote" $sql | Out-Null
}

function Test-RepresentativeData {
  param([string]$Container)
  $checks = [ordered]@{}
  $checks.user = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT count(*) FROM users WHERE id='dr-user-1';" | Select-Object -Last 1).ToString().Trim() -eq "1"
  $checks.note = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT count(*) FROM notes WHERE id='dr-note-1' AND version=3;" | Select-Object -Last 1).ToString().Trim() -eq "1"
  $checks.media = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT convert_from(decode(data_base64,'base64'),'UTF8') FROM note_media WHERE id='dr-media-1';" | Select-Object -Last 1).ToString().Trim() -eq "AiNote DR media fixture"
  $checks.vector = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT vector_dims(embedding) FROM langchain4j_embeddings WHERE embedding_id='11111111-1111-1111-1111-111111111111';" | Select-Object -Last 1).ToString().Trim() -eq "1024"
  $checks.longTermMemory = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT count(*) FROM semantic_memories WHERE content_hash='dr-memory-content-hash';" | Select-Object -Last 1).ToString().Trim() -eq "1"
  $checks.shortTermMemory = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT count(*) FROM user_memories WHERE user_id='dr-user-1';" | Select-Object -Last 1).ToString().Trim() -eq "2"
  $checks.review = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT count(*) FROM memory_review_cases WHERE user_id='dr-user-1';" | Select-Object -Last 1).ToString().Trim() -eq "1"
  $checks.audit = (Invoke-AiNotePsql $Container "ainote_dr" "ainote" "SELECT count(*) FROM admin_access_audits WHERE reason='dr_fixture';" | Select-Object -Last 1).ToString().Trim() -eq "1"
  return $checks
}

try {
  if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) { throw "Java 17 not found: $JavaPath" }
  if (-not (Test-Path -LiteralPath $BackendJar -PathType Leaf)) { throw "Backend jar not found: $BackendJar" }
  Invoke-AiNoteNative docker @("version", "--format", "{{.Server.Version}}") | Out-Null
  foreach ($container in $containers) {
    if ($container -notmatch '^ainote-dr-') { throw "Unsafe drill container name: $container" }
    if (-not (Test-ContainerAbsent $container)) { throw "Drill container already exists: $container" }
  }

  $databasePassword = New-RandomBase64 24
  [Environment]::SetEnvironmentVariable("AINOTE_BACKUP_KEY_BASE64", (New-RandomBase64 32), "Process")
  Start-PostgresContainer $sourcePostgres $databasePassword
  Invoke-AiNoteNative docker @("run", "-d", "--name", $sourceRedis, "-p", "127.0.0.1::6379", "redis:7-alpine") | Out-Null
  Wait-Redis $sourceRedis
  $postgresPort = ((Invoke-AiNoteNative docker @("port", $sourcePostgres, "5432/tcp") | Select-Object -Last 1) -split ':')[-1].Trim()
  $redisPort = ((Invoke-AiNoteNative docker @("port", $sourceRedis, "6379/tcp") | Select-Object -Last 1) -split ':')[-1].Trim()
  $backendPort = Get-FreeTcpPort
  $backendUrl = "http://127.0.0.1:$backendPort"
  $environment = @{
    SPRING_PROFILES_ACTIVE = "prod"
    SERVER_PORT = $backendPort
    SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$postgresPort/ainote_dr?stringtype=unspecified"
    SPRING_DATASOURCE_USERNAME = "ainote"
    SPRING_DATASOURCE_PASSWORD = $databasePassword
    SPRING_DATA_REDIS_HOST = "127.0.0.1"
    SPRING_DATA_REDIS_PORT = $redisPort
    NEO4J_ENABLED = "false"
    JWT_SECRET = New-RandomBase64 48
    DEEPSEEK_API_KEY = "dr-local-no-external-model-call"
    MEMORY_CHAT_HISTORY_COMPACTION_ENABLED = "false"
    MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS = "always"
  }
  $previousEnvironment = Set-ProcessEnvironment $environment
  try {
    $backendProcess = Start-Process -FilePath $JavaPath `
      -ArgumentList @("-Xms256m", "-Xmx768m", "-jar", $BackendJar) `
      -WorkingDirectory (Split-Path -Parent $BackendJar) -WindowStyle Hidden `
      -RedirectStandardOutput (Join-Path $runDirectory "migration-backend.out.log") `
      -RedirectStandardError (Join-Path $runDirectory "migration-backend.err.log") -PassThru
  } finally {
    Restore-ProcessEnvironment $previousEnvironment
  }
  Wait-Backend $backendUrl $backendProcess
  Stop-Backend $backendProcess
  $backendProcess = $null
  Seed-RepresentativeData $sourcePostgres
  $sourceChecks = Test-RepresentativeData $sourcePostgres
  if (@($sourceChecks.Values | Where-Object { -not $_ }).Count -gt 0) { throw "Source fixture validation failed" }

  $backupResult = Invoke-PowerShellFile (Join-Path $PSScriptRoot "backup-ainote.ps1") @(
    "-PostgresContainer", $sourcePostgres,
    "-Database", "ainote_dr",
    "-DatabaseUser", "ainote",
    "-OutputRoot", (Join-Path $runDirectory "backups"),
    "-JavaPath", $JavaPath)
  if ($backupResult.ExitCode -ne 0) { throw "Backup script failed: $($backupResult.Output -join ' | ')" }
  $backupPackage = Get-OutputValue $backupResult.Output "package"

  Remove-DrillContainer $sourceRedis
  Remove-DrillContainer $sourcePostgres
  if (-not (Test-ContainerAbsent $sourcePostgres)) { throw "Source PostgreSQL still exists after destruction step" }
  $sourceDestroyedAt = [DateTimeOffset]::UtcNow

  Start-PostgresContainer $targetPostgres $databasePassword
  $restoreStartedAt = [DateTimeOffset]::UtcNow
  $restoreResult = Invoke-PowerShellFile (Join-Path $PSScriptRoot "restore-ainote.ps1") @(
    "-PackageDirectory", $backupPackage,
    "-PostgresContainer", $targetPostgres,
    "-Database", "ainote_dr",
    "-DatabaseUser", "ainote",
    "-OutputRoot", (Join-Path $runDirectory "restores"),
    "-JavaPath", $JavaPath)
  $restoreFinishedAt = [DateTimeOffset]::UtcNow
  if ($restoreResult.ExitCode -ne 0) { throw "Restore script failed: $($restoreResult.Output -join ' | ')" }
  $restoreReportPath = Get-OutputValue $restoreResult.Output "report"
  $restoreReport = Get-Content -LiteralPath $restoreReportPath -Raw | ConvertFrom-Json
  if (-not $restoreReport.qualityGatePassed) { throw "Restore report quality gate failed" }
  $representativeChecks = Test-RepresentativeData $targetPostgres
  if (@($representativeChecks.Values | Where-Object { -not $_ }).Count -gt 0) {
    throw "Representative restored data validation failed"
  }

  $nonEmptyBefore = (Invoke-AiNotePsql $targetPostgres "ainote_dr" "ainote" "SELECT count(*) FROM users;" | Select-Object -Last 1).ToString().Trim()
  $secondRestore = Invoke-PowerShellFile (Join-Path $PSScriptRoot "restore-ainote.ps1") @(
    "-PackageDirectory", $backupPackage,
    "-PostgresContainer", $targetPostgres,
    "-Database", "ainote_dr",
    "-DatabaseUser", "ainote",
    "-OutputRoot", (Join-Path $runDirectory "nonempty-rejection"),
    "-JavaPath", $JavaPath)
  $nonEmptyAfter = (Invoke-AiNotePsql $targetPostgres "ainote_dr" "ainote" "SELECT count(*) FROM users;" | Select-Object -Last 1).ToString().Trim()
  $nonEmptyTargetRejected = $secondRestore.ExitCode -ne 0 -and $nonEmptyBefore -eq $nonEmptyAfter
  if (-not $nonEmptyTargetRejected) { throw "Non-empty target rejection test failed" }

  Start-PostgresContainer $corruptTargetPostgres $databasePassword
  $corruptPackage = Join-Path $runDirectory "corrupt-package"
  Copy-Item -LiteralPath $backupPackage -Destination $corruptPackage -Recurse
  $corruptArtifact = Join-Path $corruptPackage "postgres.dump.aesgcm"
  $stream = [IO.File]::Open($corruptArtifact, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
  try {
    $position = [Math]::Max(64L, [long]($stream.Length / 2))
    $stream.Position = $position
    $original = $stream.ReadByte()
    if ($original -lt 0) { throw "Corruption fixture artifact is unexpectedly empty" }
    $stream.Position = $position
    $stream.WriteByte($original -bxor 1)
  } finally {
    $stream.Dispose()
  }
  $corruptManifestPath = Join-Path $corruptPackage "manifest.json"
  $corruptManifest = Get-Content -LiteralPath $corruptManifestPath -Raw | ConvertFrom-Json
  $corruptManifest.artifact.ciphertextSha256 = Get-AiNoteSha256 $corruptArtifact
  Write-AiNoteJson $corruptManifest $corruptManifestPath
  $corruptKey = Get-AiNoteBackupKey "AINOTE_BACKUP_KEY_BASE64"
  try {
    $corruptManifestHmacPath = Join-Path $corruptPackage "manifest.hmac"
    [IO.File]::WriteAllText(
      $corruptManifestHmacPath, "$(Get-AiNoteManifestHmac $corruptManifestPath $corruptKey)`n",
      [Text.UTF8Encoding]::new($false))
  } finally {
    [Array]::Clear($corruptKey, 0, $corruptKey.Length)
  }
  $corruptManifestSha = Get-AiNoteSha256 $corruptManifestPath
  $corruptManifestHmacSha = Get-AiNoteSha256 $corruptManifestHmacPath
  $corruptChecksums = "$($corruptManifest.artifact.ciphertextSha256)  postgres.dump.aesgcm`n$corruptManifestSha  manifest.json`n$corruptManifestHmacSha  manifest.hmac`n"
  [IO.File]::WriteAllText((Join-Path $corruptPackage "checksums.sha256"), $corruptChecksums, [Text.UTF8Encoding]::new($false))
  $corruptRestore = Invoke-PowerShellFile (Join-Path $PSScriptRoot "restore-ainote.ps1") @(
    "-PackageDirectory", $corruptPackage,
    "-PostgresContainer", $corruptTargetPostgres,
    "-Database", "ainote_dr",
    "-DatabaseUser", "ainote",
    "-OutputRoot", (Join-Path $runDirectory "corruption-rejection"),
    "-JavaPath", $JavaPath)
  $corruptionRejected = $corruptRestore.ExitCode -ne 0
  $corruptTargetUntouched = Test-AiNoteDatabaseEmpty $corruptTargetPostgres "ainote_dr" "ainote"
  if (-not $corruptionRejected -or -not $corruptTargetUntouched) {
    throw "Authenticated corruption rejection test failed"
  }
} catch {
  $failures.Add($_.Exception.Message)
} finally {
  Stop-Backend $backendProcess
  foreach ($container in $containers) {
    if (-not (Test-ContainerAbsent $container)) { Remove-DrillContainer $container }
  }
  $cleanupPassed = @($containers | Where-Object { -not (Test-ContainerAbsent $_) }).Count -eq 0
  [Environment]::SetEnvironmentVariable("AINOTE_BACKUP_KEY_BASE64", $previousBackupKey, "Process")
  $keyDestroyed = [string]::IsNullOrWhiteSpace($previousBackupKey) -and
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("AINOTE_BACKUP_KEY_BASE64", "Process"))
}

if (-not $cleanupPassed) { $failures.Add("cleanup:drill_containers_remain") }
if (-not $keyDestroyed -and [string]::IsNullOrWhiteSpace($previousBackupKey)) { $failures.Add("cleanup:ephemeral_key_remains") }
$passed = $failures.Count -eq 0
$manifest = if ($null -ne $backupPackage) {
  Get-Content -LiteralPath (Join-Path $backupPackage "manifest.json") -Raw | ConvertFrom-Json
} else { $null }
$report = [ordered]@{
  schemaVersion = "ainote-backup-restore-drill-v1"
  status = if ($passed) { "PASSED" } else { "FAILED" }
  qualityGatePassed = $passed
  evidenceClass = "managed-local"
  productionProven = $false
  runId = $runId
  startedAt = $startedAt.ToString("o")
  finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
  source = [ordered]@{
    isolated = $true
    removedBeforeRestore = $null -ne $sourceDestroyedAt
    destroyedAt = if ($null -ne $sourceDestroyedAt) { $sourceDestroyedAt.ToString("o") } else { $null }
    flywayVersion = if ($null -ne $manifest) { $manifest.source.flywayVersion } else { $null }
  }
  backup = [ordered]@{
    package = $backupPackage
    backupId = if ($null -ne $manifest) { $manifest.backupId } else { $null }
    encrypted = $null -ne $manifest -and $manifest.artifact.encryption -eq "AES-256-GCM"
    ciphertextSha256 = if ($null -ne $manifest) { $manifest.artifact.ciphertextSha256 } else { $null }
    tableCount = if ($null -ne $manifest) { @($manifest.tableInventory).Count } else { 0 }
    sequenceCount = if ($null -ne $manifest) { @($manifest.sequenceInventory).Count } else { 0 }
  }
  restore = [ordered]@{
    report = $restoreReportPath
    databaseRtoMilliseconds = if ($null -ne $restoreStartedAt -and $null -ne $restoreFinishedAt) {
      [long]($restoreFinishedAt - $restoreStartedAt).TotalMilliseconds
    } else { $null }
    rpoSecondsAtRestoreStart = if ($null -ne $manifest -and $null -ne $restoreStartedAt) {
      [Math]::Round(($restoreStartedAt - [DateTimeOffset]::Parse($manifest.completedAt)).TotalSeconds, 3)
    } else { $null }
    representativeChecks = $representativeChecks
  }
  negativeTests = [ordered]@{
    authenticatedCorruptionRejected = $corruptionRejected
    corruptTargetUntouched = $corruptTargetUntouched
    nonEmptyTargetRejected = $nonEmptyTargetRejected
  }
  recoveryBoundary = [ordered]@{
    redis = "start_empty_and_reauthenticate"
    jwt = "rotate_secret"
    neo4j = "rebuild_per_user_from_postgresql"
    productionPitr = "not_proven"
  }
  cleanup = [ordered]@{
    containersRemoved = $cleanupPassed
    ephemeralKeyDestroyed = $keyDestroyed
  }
  failures = @($failures)
}
$reportPath = Join-Path $runDirectory "backup-restore-drill-report.json"
Write-AiNoteJson $report $reportPath
[IO.File]::WriteAllText((Join-Path $OutputRoot "latest.json"), ($report | ConvertTo-Json -Depth 16), [Text.UTF8Encoding]::new($false))
Write-Output "report=$reportPath"
Write-Output "status=$($report.status)"
Write-Output "cleanupPassed=$cleanupPassed"
if (-not $passed) { exit 1 }
