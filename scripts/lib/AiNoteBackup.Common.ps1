Set-StrictMode -Version Latest

$script:AiNoteBackupCryptoSourcePath = Join-Path $PSScriptRoot "AiNoteBackupCrypto.java"

function Assert-AiNoteSafeToken {
  param([Parameter(Mandatory)][string]$Value, [Parameter(Mandatory)][string]$Name)
  if ($Value -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "$Name contains unsupported characters"
  }
}

function Invoke-AiNoteNative {
  param(
    [Parameter(Mandatory)][string]$FilePath,
    [string[]]$Arguments = @()
  )
  $output = @(& $FilePath @Arguments 2>&1)
  if ($LASTEXITCODE -ne 0) {
    $summary = ($output | Select-Object -Last 20) -join [Environment]::NewLine
    throw "Native command failed with exit code $LASTEXITCODE`: $FilePath`n$summary"
  }
  return $output
}

function Get-AiNoteSha256 {
  param([Parameter(Mandatory)][string]$Path)
  return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Write-AiNoteJson {
  param([Parameter(Mandatory)]$Value, [Parameter(Mandatory)][string]$Path)
  $json = $Value | ConvertTo-Json -Depth 16
  [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Get-AiNoteBackupKey {
  param([Parameter(Mandatory)][string]$EnvironmentVariable)
  if ($EnvironmentVariable -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "Invalid backup key environment-variable name"
  }
  $encoded = [Environment]::GetEnvironmentVariable($EnvironmentVariable, "Process")
  if ([string]::IsNullOrWhiteSpace($encoded)) {
    throw "Backup encryption key is missing from environment variable $EnvironmentVariable"
  }
  try {
    $key = [Convert]::FromBase64String($encoded)
  } catch {
    throw "Backup encryption key must be valid Base64"
  }
  if ($key.Length -ne 32) {
    [Array]::Clear($key, 0, $key.Length)
    throw "Backup encryption key must decode to exactly 32 bytes"
  }
  return $key
}

function Get-AiNoteManifestHmac {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][byte[]]$Key)
  $context = [Text.Encoding]::ASCII.GetBytes("ainote-backup-manifest-key-v1")
  $deriver = [Security.Cryptography.HMACSHA256]::new($Key)
  try {
    $derivedKey = $deriver.ComputeHash($context)
  } finally {
    $deriver.Dispose()
  }
  $authenticator = [Security.Cryptography.HMACSHA256]::new($derivedKey)
  $stream = [IO.File]::OpenRead($Path)
  try {
    return ([BitConverter]::ToString($authenticator.ComputeHash($stream))).Replace("-", "")
  } finally {
    $stream.Dispose()
    $authenticator.Dispose()
    [Array]::Clear($derivedKey, 0, $derivedKey.Length)
  }
}

function Test-AiNoteFixedTimeHexEquals {
  param([Parameter(Mandatory)][string]$Expected, [Parameter(Mandatory)][string]$Actual)
  if ($Expected -notmatch '^[A-Fa-f0-9]{64}$' -or $Actual -notmatch '^[A-Fa-f0-9]{64}$') { return $false }
  $expectedBytes = New-Object byte[] 32
  $actualBytes = New-Object byte[] 32
  for ($index = 0; $index -lt 32; $index++) {
    $expectedBytes[$index] = [Convert]::ToByte($Expected.Substring($index * 2, 2), 16)
    $actualBytes[$index] = [Convert]::ToByte($Actual.Substring($index * 2, 2), 16)
  }
  try {
    $difference = 0
    for ($index = 0; $index -lt 32; $index++) {
      $difference = $difference -bor ($expectedBytes[$index] -bxor $actualBytes[$index])
    }
    return $difference -eq 0
  } finally {
    [Array]::Clear($expectedBytes, 0, $expectedBytes.Length)
    [Array]::Clear($actualBytes, 0, $actualBytes.Length)
  }
}

function Invoke-AiNoteBackupCrypto {
  param(
    [Parameter(Mandatory)][ValidateSet("encrypt", "decrypt")][string]$Mode,
    [Parameter(Mandatory)][string]$InputPath,
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][string]$JavaPath,
    [Parameter(Mandatory)][string]$KeyEnvironmentVariable
  )
  if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) {
    throw "Java 17 executable not found: $JavaPath"
  }
  $cryptoSource = $script:AiNoteBackupCryptoSourcePath
  if (-not (Test-Path -LiteralPath $cryptoSource -PathType Leaf)) {
    throw "Backup crypto helper not found: $cryptoSource"
  }
  Invoke-AiNoteNative $JavaPath @($cryptoSource, $Mode, $InputPath, $OutputPath, $KeyEnvironmentVariable) | Out-Null
}

function Assert-AiNotePostgresContainer {
  param(
    [Parameter(Mandatory)][string]$Container,
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$User
  )
  Assert-AiNoteSafeToken $Container "Container"
  Assert-AiNoteSafeToken $Database "Database"
  Assert-AiNoteSafeToken $User "User"
  $running = (Invoke-AiNoteNative docker @("inspect", "-f", "{{.State.Running}}", $Container) | Select-Object -Last 1).ToString().Trim()
  if ($running -ne "true") { throw "PostgreSQL container is not running: $Container" }
  Invoke-AiNoteNative docker @("exec", $Container, "pg_isready", "-U", $User, "-d", $Database) | Out-Null
}

function Invoke-AiNotePsql {
  param(
    [Parameter(Mandatory)][string]$Container,
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$User,
    [Parameter(Mandatory)][string]$Sql
  )
  return Invoke-AiNoteNative docker @(
    "exec", $Container, "psql", "-X", "-U", $User, "-d", $Database,
    "-At", "-v", "ON_ERROR_STOP=1", "-c", $Sql)
}

function Get-AiNotePostgresInventory {
  param(
    [Parameter(Mandatory)][string]$Container,
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$User
  )
  $rows = Invoke-AiNotePsql $Container $Database $User "SELECT schemaname || E'\t' || tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY schemaname, tablename;"
  $inventory = [System.Collections.Generic.List[object]]::new()
  foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace("$row")) { continue }
    $parts = "$row" -split "`t", 2
    $schema = $parts[0]
    $table = $parts[1]
    if ($schema -notmatch '^[A-Za-z0-9_]+$' -or $table -notmatch '^[A-Za-z0-9_]+$') {
      throw "Unsupported PostgreSQL identifier in inventory"
    }
    $count = (Invoke-AiNotePsql $Container $Database $User "SELECT count(*) FROM `"$schema`".`"$table`";" | Select-Object -Last 1).ToString().Trim()
    $inventory.Add([ordered]@{ schema = $schema; table = $table; rowCount = [long]$count })
  }
  return @($inventory)
}

function Get-AiNotePostgresSequences {
  param(
    [Parameter(Mandatory)][string]$Container,
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$User
  )
  $rows = Invoke-AiNotePsql $Container $Database $User "SELECT schemaname || E'\t' || sequencename FROM pg_sequences WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY schemaname, sequencename;"
  $inventory = [System.Collections.Generic.List[object]]::new()
  foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace("$row")) { continue }
    $parts = "$row" -split "`t", 2
    $schema = $parts[0]
    $sequence = $parts[1]
    if ($schema -notmatch '^[A-Za-z0-9_]+$' -or $sequence -notmatch '^[A-Za-z0-9_]+$') {
      throw "Unsupported PostgreSQL sequence identifier in inventory"
    }
    $state = (Invoke-AiNotePsql $Container $Database $User "SELECT last_value::text || E'\t' || is_called::text FROM `"$schema`".`"$sequence`";" | Select-Object -Last 1).ToString().Trim() -split "`t", 2
    $inventory.Add([ordered]@{
      schema = $schema
      sequence = $sequence
      lastValue = [long]$state[0]
      isCalled = @("t", "true", "1") -contains $state[1].ToLowerInvariant()
    })
  }
  return @($inventory)
}

function Get-AiNoteFlywayVersion {
  param([string]$Container, [string]$Database, [string]$User)
  $values = @(Invoke-AiNotePsql $Container $Database $User "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;")
  if ($values.Count -eq 0) { throw "Flyway schema history is missing" }
  return ($values | Select-Object -Last 1).ToString().Trim()
}

function Test-AiNoteDatabaseEmpty {
  param([string]$Container, [string]$Database, [string]$User)
  $count = (Invoke-AiNotePsql $Container $Database $User "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema');" | Select-Object -Last 1).ToString().Trim()
  return [long]$count -eq 0
}

function Get-AiNoteObjectValue {
  param([Parameter(Mandatory)]$Item, [Parameter(Mandatory)][string]$Property)
  if ($Item -is [Collections.IDictionary]) {
    return $Item[$Property]
  }
  $entry = $Item.PSObject.Properties[$Property]
  if ($null -eq $entry) { throw "Inventory property is missing: $Property" }
  return $entry.Value
}

function Compare-AiNoteInventory {
  param([object[]]$Expected, [object[]]$Actual, [string[]]$KeyProperties, [string[]]$ValueProperties)
  $expectedMap = @{}
  foreach ($item in @($Expected)) {
    $key = ($KeyProperties | ForEach-Object { "$(Get-AiNoteObjectValue $item $_)" }) -join "/"
    $expectedMap[$key] = $item
  }
  $actualMap = @{}
  foreach ($item in @($Actual)) {
    $key = ($KeyProperties | ForEach-Object { "$(Get-AiNoteObjectValue $item $_)" }) -join "/"
    $actualMap[$key] = $item
  }
  $failures = [System.Collections.Generic.List[string]]::new()
  foreach ($key in @($expectedMap.Keys + $actualMap.Keys | Sort-Object -Unique)) {
    if (-not $expectedMap.ContainsKey($key)) { $failures.Add("unexpected:$key"); continue }
    if (-not $actualMap.ContainsKey($key)) { $failures.Add("missing:$key"); continue }
    foreach ($property in $ValueProperties) {
      $expectedValue = Get-AiNoteObjectValue $expectedMap[$key] $property
      $actualValue = Get-AiNoteObjectValue $actualMap[$key] $property
      if ("$expectedValue" -ne "$actualValue") {
        $failures.Add("mismatch:$key`:$property expected=$expectedValue actual=$actualValue")
      }
    }
  }
  return @($failures)
}
