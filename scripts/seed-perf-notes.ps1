param(
  [Parameter(Mandatory = $true)]
  [string]$UserId,
  [int]$Count = 10000,
  [string]$PostgresContainer = "ainote-postgres",
  [string]$DbUser = "ainote",
  [string]$DbName = "ainote",
  [switch]$Cleanup
)

$ErrorActionPreference = "Stop"

if ($Count -lt 1) {
  throw "Count must be positive"
}
if ($UserId -notmatch '^[A-Za-z0-9._:@-]{1,128}$') {
  throw "Refusing to seed notes for an unsafe user id: $UserId"
}

$safeUserId = $UserId.Replace("'", "''")

if ($Cleanup) {
  $sql = @"
DELETE FROM notes
WHERE user_id = '$safeUserId'
  AND id LIKE 'perf-note-%';
"@
  $sql | docker exec -i $PostgresContainer psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 | Out-Host
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to clean perf notes"
  }
  Write-Output "perf-notes-cleaned=true"
  return
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$sql = @"
DELETE FROM notes
WHERE user_id = '$safeUserId'
  AND id LIKE 'perf-note-%';

INSERT INTO notes (
  id,
  title,
  content,
  user_id,
  folder_id,
  created_at,
  updated_at,
  deleted_at,
  pinned,
  starred,
  archived,
  version
)
SELECT
  'perf-note-$runId-' || gs,
  'Perf pagination ' || gs,
  'Seeded performance note ' || gs,
  '$safeUserId',
  NULL,
  NOW() - (gs || ' seconds')::interval,
  NOW() - (gs || ' seconds')::interval,
  NULL,
  FALSE,
  FALSE,
  FALSE,
  0
FROM generate_series(1, $Count) AS gs;
"@

$sql | docker exec -i $PostgresContainer psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 | Out-Host
if ($LASTEXITCODE -ne 0) {
  throw "Failed to seed perf notes"
}

Write-Output "perf-notes-run-id=$runId"
Write-Output "perf-notes-seeded=$Count"
