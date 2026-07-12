param(
  [Parameter(Mandatory)][string]$PackageDirectory,
  [Parameter(Mandatory)][string]$PostgresContainer,
  [string]$Database = "ainote",
  [string]$DatabaseUser = "ainote",
  [string]$OutputRoot = "",
  [string]$JavaPath = "D:\Java\jdk17\bin\java.exe",
  [string]$EncryptionKeyEnvironmentVariable = "AINOTE_BACKUP_KEY_BASE64",
  [switch]$AllowNonEmptyTarget
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "lib\AiNoteBackup.Common.ps1")

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $RepoRoot "backend\target\backup-restores"
}
[IO.Directory]::CreateDirectory($OutputRoot) | Out-Null
$startedAt = [DateTimeOffset]::UtcNow
$runId = "restore-$($startedAt.ToString('yyyyMMddTHHmmssZ'))-$([Guid]::NewGuid().ToString('N').Substring(0,8))"
$runDirectory = Join-Path $OutputRoot $runId
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$plainDump = Join-Path $env:TEMP "$runId.dump"
$containerDump = "/tmp/$runId.dump"
$key = $null
$failures = [System.Collections.Generic.List[string]]::new()
$manifest = $null
$targetInventory = @()
$targetSequences = @()
$targetFlyway = $null
$manifestAuthenticated = $false
$aeadAuthenticated = $false
$inventoryMatched = $false

try {
  $manifestPath = Join-Path $PackageDirectory "manifest.json"
  if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Backup manifest is missing" }
  $checksumsPath = Join-Path $PackageDirectory "checksums.sha256"
  if (-not (Test-Path -LiteralPath $checksumsPath -PathType Leaf)) { throw "Backup checksums are missing" }
  $manifestHmacPath = Join-Path $PackageDirectory "manifest.hmac"
  if (-not (Test-Path -LiteralPath $manifestHmacPath -PathType Leaf)) { throw "Backup manifest authentication is missing" }
  $checksumLines = @(Get-Content -LiteralPath $checksumsPath)
  $manifestChecksum = $checksumLines | Where-Object { $_ -match '^[A-Fa-f0-9]{64}  manifest\.json$' } | Select-Object -First 1
  if ($null -eq $manifestChecksum -or (Get-AiNoteSha256 $manifestPath) -ne ($manifestChecksum -split '  ')[0].ToUpperInvariant()) {
    throw "Backup manifest SHA-256 mismatch"
  }
  $manifestHmacChecksum = $checksumLines | Where-Object { $_ -match '^[A-Fa-f0-9]{64}  manifest\.hmac$' } | Select-Object -First 1
  if ($null -eq $manifestHmacChecksum -or (Get-AiNoteSha256 $manifestHmacPath) -ne ($manifestHmacChecksum -split '  ')[0].ToUpperInvariant()) {
    throw "Backup manifest HMAC file SHA-256 mismatch"
  }
  $key = Get-AiNoteBackupKey $EncryptionKeyEnvironmentVariable
  $expectedManifestHmac = (Get-Content -LiteralPath $manifestHmacPath -Raw).Trim()
  $actualManifestHmac = Get-AiNoteManifestHmac $manifestPath $key
  if (-not (Test-AiNoteFixedTimeHexEquals $expectedManifestHmac $actualManifestHmac)) {
    throw "Backup manifest HMAC authentication failed"
  }
  $manifestAuthenticated = $true
  $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
  if ($manifest.schemaVersion -ne "ainote-backup-v1" -or $manifest.status -ne "COMPLETE") {
    throw "Unsupported or incomplete backup manifest"
  }
  if ($manifest.artifact.file -ne "postgres.dump.aesgcm") { throw "Manifest artifact name is not allowed" }
  $artifactPath = Join-Path $PackageDirectory $manifest.artifact.file
  if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) { throw "Encrypted backup artifact is missing" }
  if ((Get-AiNoteSha256 $artifactPath) -ne $manifest.artifact.ciphertextSha256) {
    throw "Encrypted backup SHA-256 mismatch"
  }
  Invoke-AiNoteBackupCrypto "decrypt" $artifactPath $plainDump $JavaPath $EncryptionKeyEnvironmentVariable
  $aeadAuthenticated = $true
  if ((Get-AiNoteSha256 $plainDump) -ne $manifest.artifact.plaintextSha256) {
    throw "Decrypted backup SHA-256 mismatch"
  }

  Assert-AiNotePostgresContainer $PostgresContainer $Database $DatabaseUser
  $targetWasEmpty = Test-AiNoteDatabaseEmpty $PostgresContainer $Database $DatabaseUser
  if (-not $targetWasEmpty -and -not $AllowNonEmptyTarget) {
    throw "Target database is not empty; restore refused"
  }
  Invoke-AiNoteNative docker @("cp", $plainDump, "${PostgresContainer}:$containerDump") | Out-Null
  Invoke-AiNoteNative docker @("exec", $PostgresContainer, "pg_restore", "--list", $containerDump) | Out-Null
  $restoreArguments = @(
    "exec", $PostgresContainer, "pg_restore", "-U", $DatabaseUser, "-d", $Database,
    "--exit-on-error", "--no-owner", "--no-acl")
  if ($AllowNonEmptyTarget) { $restoreArguments += @("--clean", "--if-exists") }
  $restoreArguments += $containerDump
  Invoke-AiNoteNative docker $restoreArguments | Out-Null

  $targetFlyway = Get-AiNoteFlywayVersion $PostgresContainer $Database $DatabaseUser
  $targetInventory = @(Get-AiNotePostgresInventory $PostgresContainer $Database $DatabaseUser)
  $targetSequences = @(Get-AiNotePostgresSequences $PostgresContainer $Database $DatabaseUser)
  if ($targetFlyway -ne $manifest.source.flywayVersion) {
    $failures.Add("flyway:mismatch expected=$($manifest.source.flywayVersion) actual=$targetFlyway")
  }
  @(Compare-AiNoteInventory @($manifest.tableInventory) $targetInventory @("schema", "table") @("rowCount")) |
    ForEach-Object { $failures.Add("table:$_") }
  @(Compare-AiNoteInventory @($manifest.sequenceInventory) $targetSequences @("schema", "sequence") @("lastValue", "isCalled")) |
    ForEach-Object { $failures.Add("sequence:$_") }
  if ($failures.Count -gt 0) { throw "Post-restore validation failed" }
  $inventoryMatched = $true
} catch {
  $failures.Add($_.Exception.Message)
} finally {
  & docker exec $PostgresContainer rm -f $containerDump 2>$null | Out-Null
  if (Test-Path -LiteralPath $plainDump) { Remove-Item -LiteralPath $plainDump -Force }
  if ($null -ne $key) { [Array]::Clear($key, 0, $key.Length) }
}

$passed = $failures.Count -eq 0
$report = [ordered]@{
  schemaVersion = "ainote-restore-report-v1"
  status = if ($passed) { "PASSED" } else { "FAILED" }
  qualityGatePassed = $passed
  evidenceClass = "managed-local"
  productionProven = $false
  runId = $runId
  backupId = if ($null -ne $manifest) { $manifest.backupId } else { $null }
  startedAt = $startedAt.ToString("o")
  finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
  target = [ordered]@{
    database = $Database
    flywayVersion = $targetFlyway
    tableCount = @($targetInventory).Count
    sequenceCount = @($targetSequences).Count
  }
  verification = [ordered]@{
    encryptedArtifactSha256 = if ($null -ne $manifest) { $manifest.artifact.ciphertextSha256 } else { $null }
    manifestHmacAuthenticated = $manifestAuthenticated
    aeadAuthenticated = $aeadAuthenticated
    inventoryMatched = $inventoryMatched
  }
  recoveryActions = @(
    "Provision matching Git release and external secrets",
    "Start Redis empty and require every user to sign in again",
    "Rotate JWT secret",
    "Start Neo4j empty and invoke POST /api/graph/rebuild for every user"
  )
  failures = @($failures)
}
$reportPath = Join-Path $runDirectory "restore-report.json"
Write-AiNoteJson $report $reportPath
Write-Output "report=$reportPath"
Write-Output "status=$($report.status)"
if (-not $passed) { exit 1 }
