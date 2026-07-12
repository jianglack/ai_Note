param(
  [Parameter(Mandatory)][string]$PostgresContainer,
  [string]$Database = "ainote",
  [string]$DatabaseUser = "ainote",
  [string]$OutputRoot = "",
  [string]$JavaPath = "D:\Java\jdk17\bin\java.exe",
  [string]$EncryptionKeyEnvironmentVariable = "AINOTE_BACKUP_KEY_BASE64",
  [bool]$RequireQuiescentInventory = $true
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "lib\AiNoteBackup.Common.ps1")

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $RepoRoot "backend\target\backups"
}
[IO.Directory]::CreateDirectory($OutputRoot) | Out-Null
if (-not $RequireQuiescentInventory) {
  throw "Verified backup requires a quiescent inventory; non-quiescent mode is not supported"
}

$startedAt = [DateTimeOffset]::UtcNow
$backupId = "backup-$($startedAt.ToString('yyyyMMddTHHmmssZ'))-$([Guid]::NewGuid().ToString('N').Substring(0,8))"
$workDirectory = Join-Path $OutputRoot "$backupId.incomplete"
$packageDirectory = Join-Path $OutputRoot $backupId
[IO.Directory]::CreateDirectory($workDirectory) | Out-Null
$plainDump = Join-Path $workDirectory ".postgres.dump.plain"
$encryptedDump = Join-Path $workDirectory "postgres.dump.aesgcm"
$containerDump = "/tmp/$backupId.dump"
$key = $null

try {
  Assert-AiNotePostgresContainer $PostgresContainer $Database $DatabaseUser
  $key = Get-AiNoteBackupKey $EncryptionKeyEnvironmentVariable
  $beforeInventory = @(Get-AiNotePostgresInventory $PostgresContainer $Database $DatabaseUser)
  $beforeSequences = @(Get-AiNotePostgresSequences $PostgresContainer $Database $DatabaseUser)
  $flywayVersion = Get-AiNoteFlywayVersion $PostgresContainer $Database $DatabaseUser
  $postgresVersion = (Invoke-AiNotePsql $PostgresContainer $Database $DatabaseUser "SHOW server_version;" | Select-Object -Last 1).ToString().Trim()

  Invoke-AiNoteNative docker @(
    "exec", $PostgresContainer, "pg_dump", "-U", $DatabaseUser, "-d", $Database,
    "--format=custom", "--compress=6", "--no-owner", "--no-acl", "--file=$containerDump") | Out-Null
  Invoke-AiNoteNative docker @("cp", "${PostgresContainer}:$containerDump", $plainDump) | Out-Null

  $afterInventory = @(Get-AiNotePostgresInventory $PostgresContainer $Database $DatabaseUser)
  $afterSequences = @(Get-AiNotePostgresSequences $PostgresContainer $Database $DatabaseUser)
  if ($RequireQuiescentInventory) {
    $inventoryChanges = @(Compare-AiNoteInventory $beforeInventory $afterInventory @("schema", "table") @("rowCount"))
    $sequenceChanges = @(Compare-AiNoteInventory $beforeSequences $afterSequences @("schema", "sequence") @("lastValue", "isCalled"))
    if ($inventoryChanges.Count -gt 0 -or $sequenceChanges.Count -gt 0) {
      throw "Database changed during backup; retry in a quiescent window"
    }
  }

  $plainSha256 = Get-AiNoteSha256 $plainDump
  Invoke-AiNoteBackupCrypto "encrypt" $plainDump $encryptedDump $JavaPath $EncryptionKeyEnvironmentVariable
  $cipherSha256 = Get-AiNoteSha256 $encryptedDump
  $gitCommit = (Invoke-AiNoteNative git @("-C", $RepoRoot, "rev-parse", "HEAD") | Select-Object -Last 1).ToString().Trim()
  $gitDirty = -not [string]::IsNullOrWhiteSpace((@(& git -C $RepoRoot status --porcelain 2>$null) -join ""))

  $manifest = [ordered]@{
    schemaVersion = "ainote-backup-v1"
    backupId = $backupId
    status = "COMPLETE"
    evidenceClass = "managed-local"
    productionProven = $false
    createdAt = $startedAt.ToString("o")
    completedAt = [DateTimeOffset]::UtcNow.ToString("o")
    source = [ordered]@{
      postgresMajor = 15
      postgresVersion = $postgresVersion
      database = $Database
      flywayVersion = $flywayVersion
      gitCommit = $gitCommit
      dirtyWorktree = $gitDirty
      quiescentInventoryRequired = $RequireQuiescentInventory
    }
    artifact = [ordered]@{
      file = "postgres.dump.aesgcm"
      format = "pg_dump-custom+aead-chunks-v1"
      encryption = "AES-256-GCM"
      cryptoRuntime = "Java 17 source-file helper"
      keyReference = "env:$EncryptionKeyEnvironmentVariable"
      ciphertextSha256 = $cipherSha256
      plaintextSha256 = $plainSha256
      ciphertextBytes = (Get-Item -LiteralPath $encryptedDump).Length
    }
    components = @(
      [ordered]@{ name = "postgresql"; classification = "authoritative"; treatment = "included" },
      [ordered]@{ name = "note_media"; classification = "authoritative"; treatment = "included_in_postgresql" },
      [ordered]@{ name = "neo4j"; classification = "derived"; treatment = "rebuild_from_postgresql" },
      [ordered]@{ name = "redis"; classification = "ephemeral_token_allow_list"; treatment = "start_empty_reauthenticate" },
      [ordered]@{ name = "release_assets"; classification = "version_controlled"; treatment = "restore_matching_git_release" },
      [ordered]@{ name = "secrets"; classification = "external"; treatment = "excluded_rotate_jwt" }
    )
    tableInventory = $beforeInventory
    sequenceInventory = $beforeSequences
  }
  $manifestPath = Join-Path $workDirectory "manifest.json"
  Write-AiNoteJson $manifest $manifestPath
  $manifestHmacPath = Join-Path $workDirectory "manifest.hmac"
  [IO.File]::WriteAllText(
    $manifestHmacPath, "$(Get-AiNoteManifestHmac $manifestPath $key)`n",
    [Text.UTF8Encoding]::new($false))
  $manifestSha256 = Get-AiNoteSha256 $manifestPath
  $manifestHmacSha256 = Get-AiNoteSha256 $manifestHmacPath
  $checksumText = "$cipherSha256  postgres.dump.aesgcm`n$manifestSha256  manifest.json`n$manifestHmacSha256  manifest.hmac`n"
  [IO.File]::WriteAllText(
    (Join-Path $workDirectory "checksums.sha256"), $checksumText,
    [Text.UTF8Encoding]::new($false))
  Remove-Item -LiteralPath $plainDump -Force
  Move-Item -LiteralPath $workDirectory -Destination $packageDirectory
  Write-Output "package=$packageDirectory"
  Write-Output "backupId=$backupId"
  Write-Output "status=COMPLETE"
} finally {
  & docker exec $PostgresContainer rm -f $containerDump 2>$null | Out-Null
  if (Test-Path -LiteralPath $plainDump) { Remove-Item -LiteralPath $plainDump -Force }
  if ($null -ne $key) { [Array]::Clear($key, 0, $key.Length) }
}
