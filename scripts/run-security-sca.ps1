param(
  [string]$OsvScannerPath = "",
  [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendRoot = Join-Path $RepoRoot "backend"
$FrontendRoot = Join-Path $RepoRoot "frontend"
if ([string]::IsNullOrWhiteSpace($OsvScannerPath)) {
  $OsvScannerPath = Join-Path $BackendRoot "target\security-tools\osv-scanner-v2.4.0\osv-scanner.exe"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $BackendRoot "target\security-compliance"
}
$OsvScannerPath = [IO.Path]::GetFullPath($OsvScannerPath)
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
[IO.Directory]::CreateDirectory($OutputRoot) | Out-Null

$startedAt = [DateTimeOffset]::UtcNow
$failures = [System.Collections.Generic.List[string]]::new()
$scannerVersion = $null
$scannerSha256 = $null
$npmAudit = $null
$rawVulnerabilities = @()
$acceptedExceptions = @()
$gatedVulnerabilities = @()

function Invoke-Native([string]$file, [string[]]$arguments, [string]$workingDirectory) {
  $previous = Get-Location
  $previousErrorPreference = $ErrorActionPreference
  try {
    Set-Location $workingDirectory
    $ErrorActionPreference = "Continue"
    $output = @(& $file @arguments 2>&1)
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
  } finally {
    $ErrorActionPreference = $previousErrorPreference
    Set-Location $previous
  }
}

function Get-OsvRows([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { return @() }
  $json = Get-Content -Raw -Encoding utf8 $path | ConvertFrom-Json
  $rows = @()
  foreach ($result in @($json.results)) {
    foreach ($package in @($result.packages)) {
      foreach ($vulnerability in @($package.vulnerabilities)) {
        $rows += [pscustomobject]@{
          id = $vulnerability.id
          ecosystem = $package.package.ecosystem
          package = $package.package.name
          version = $package.package.version
          severity = $vulnerability.database_specific.severity
        }
      }
    }
  }
  return $rows
}

try {
  if (-not (Test-Path -LiteralPath $OsvScannerPath -PathType Leaf)) {
    throw "OSV Scanner is unavailable: $OsvScannerPath"
  }
  $checksumFile = Join-Path (Split-Path -Parent $OsvScannerPath) "SHA256SUMS"
  if (-not (Test-Path -LiteralPath $checksumFile -PathType Leaf)) {
    throw "OSV Scanner checksum manifest is unavailable"
  }
  $expectedSha = ((Get-Content $checksumFile | Where-Object {
    $_ -match 'osv-scanner_windows_amd64\.exe$'
  }) -split '\s+')[0].ToLowerInvariant()
  $scannerSha256 = (Get-FileHash $OsvScannerPath -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($expectedSha -ne $scannerSha256) { throw "OSV Scanner checksum verification failed" }
  $scannerVersion = (& $OsvScannerPath --version 2>&1) -join " "
  if ($LASTEXITCODE -ne 0 -or $scannerVersion -notmatch '2\.4\.0') {
    throw "Unexpected OSV Scanner version"
  }

  $npmAuditPath = Join-Path $OutputRoot "npm-audit.json"
  $npmResult = Invoke-Native "npm.cmd" @(
    "audit", "--omit=dev", "--audit-level=high", "--registry=https://registry.npmjs.org", "--json"
  ) $FrontendRoot
  $npmResult.Output -join "`n" | Set-Content -LiteralPath $npmAuditPath -Encoding utf8
  if ($npmResult.ExitCode -ne 0) { $failures.Add("npm audit reported a blocking vulnerability or was unavailable") }
  try { $npmAudit = Get-Content -Raw -Encoding utf8 $npmAuditPath | ConvertFrom-Json }
  catch { $failures.Add("npm audit report is not parseable JSON") }

  $mavenResult = Invoke-Native "mvn.cmd" @(
    "-q", "org.cyclonedx:cyclonedx-maven-plugin:2.9.2:makeAggregateBom", "-DskipTests"
  ) $BackendRoot
  if ($mavenResult.ExitCode -ne 0) {
    $failures.Add("Backend CycloneDX generation failed")
  }

  $frontendTarget = Join-Path $FrontendRoot "target"
  [IO.Directory]::CreateDirectory($frontendTarget) | Out-Null
  $frontendBom = Join-Path $frontendTarget "bom.json"
  $cmdLine = "cd /d `"$FrontendRoot`" && npm sbom --sbom-format cyclonedx > `"$frontendBom`""
  $frontendBomResult = Invoke-Native "cmd.exe" @("/d", "/c", $cmdLine) $FrontendRoot
  if ($frontendBomResult.ExitCode -ne 0) { $failures.Add("Frontend CycloneDX generation failed") }

  $backendBom = Join-Path $BackendRoot "target\bom.json"
  foreach ($bom in @($backendBom, $frontendBom)) {
    if (-not (Test-Path -LiteralPath $bom -PathType Leaf)) {
      $failures.Add("SBOM is missing: $bom")
      continue
    }
    try {
      $parsed = Get-Content -Raw -Encoding utf8 $bom | ConvertFrom-Json
      if ($parsed.bomFormat -ne "CycloneDX") { $failures.Add("SBOM has an unexpected format: $bom") }
    } catch { $failures.Add("SBOM is not parseable JSON: $bom") }
  }

  if ($failures.Count -eq 0) {
    $rawReport = Join-Path $OutputRoot "osv-sbom-report-raw.json"
    $rawResult = Invoke-Native $OsvScannerPath @(
      "scan", "source", "-L", $backendBom, "-L", $frontendBom,
      "--format", "json", "--output-file", $rawReport
    ) $RepoRoot
    if ($rawResult.ExitCode -notin @(0, 1)) { $failures.Add("OSV raw scan was unavailable") }
    $rawVulnerabilities = @(Get-OsvRows $rawReport)

    $acceptedExceptions = @($rawVulnerabilities | Where-Object {
      $_.id -eq "GHSA-5jmj-h7xm-6q6v" -and
      $_.package -eq "com.fasterxml.jackson.core:jackson-databind" -and
      $_.version -eq "2.21.5" -and
      $_.severity -eq "MODERATE"
    })
    $unexpected = @($rawVulnerabilities | Where-Object {
      -not ($_.id -eq "GHSA-5jmj-h7xm-6q6v" -and
        $_.package -eq "com.fasterxml.jackson.core:jackson-databind" -and
        $_.version -eq "2.21.5" -and
        $_.severity -eq "MODERATE")
    })
    if ($unexpected.Count -gt 0) { $failures.Add("OSV found vulnerabilities outside the reviewed exception") }

    $gatedReport = Join-Path $OutputRoot "osv-sbom-report-gated.json"
    $config = Join-Path $RepoRoot "security\osv-scanner.toml"
    $gatedResult = Invoke-Native $OsvScannerPath @(
      "scan", "source", "-L", $backendBom, "-L", $frontendBom,
      "--config", $config, "--format", "json", "--output-file", $gatedReport
    ) $RepoRoot
    if ($gatedResult.ExitCode -ne 0) { $failures.Add("OSV gated scan failed") }
    $gatedVulnerabilities = @(Get-OsvRows $gatedReport)
    if ($gatedVulnerabilities.Count -ne 0) { $failures.Add("OSV gated report is not empty") }
  }

  $backendBomHash = if (Test-Path $backendBom) { (Get-FileHash $backendBom -Algorithm SHA256).Hash } else { $null }
  $frontendBomHash = if (Test-Path $frontendBom) { (Get-FileHash $frontendBom -Algorithm SHA256).Hash } else { $null }
} catch {
  $failures.Add($_.Exception.Message)
}

$passed = $failures.Count -eq 0
$report = [ordered]@{
  schemaVersion = "security-sca-v1"
  status = $(if ($passed) { "PASSED" } else { "FAILED" })
  qualityGatePassed = $passed
  evidenceClass = "managed-local"
  productionProven = $false
  startedAt = $startedAt.ToString("o")
  finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
  scanner = [ordered]@{ version = $scannerVersion; sha256 = $scannerSha256; available = $null -ne $scannerVersion }
  npmAudit = [ordered]@{
    high = $npmAudit.metadata.vulnerabilities.high
    critical = $npmAudit.metadata.vulnerabilities.critical
    total = $npmAudit.metadata.vulnerabilities.total
  }
  osv = [ordered]@{
    rawVulnerabilityCount = @($rawVulnerabilities).Count
    acceptedExceptionCount = @($acceptedExceptions).Count
    gatedVulnerabilityCount = @($gatedVulnerabilities).Count
    exceptionId = "GHSA-5jmj-h7xm-6q6v"
    exceptionExpires = "2026-08-11"
    exceptionEvidence = "jackson-databind 2.21.5 is outside the GitHub Reviewed affected range >=2.19.0,<2.21.5"
  }
  sbom = [ordered]@{
    backend = [ordered]@{ path = $backendBom; sha256 = $backendBomHash }
    frontend = [ordered]@{ path = $frontendBom; sha256 = $frontendBomHash }
  }
  failures = $failures
}
$reportPath = Join-Path $OutputRoot "supply-chain-security-report.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Output "status=$($report.status)"
Write-Output "qualityGatePassed=$passed"
Write-Output "npmHigh=$($report.npmAudit.high)"
Write-Output "npmCritical=$($report.npmAudit.critical)"
Write-Output "osvRaw=$($report.osv.rawVulnerabilityCount)"
Write-Output "osvAcceptedExceptions=$($report.osv.acceptedExceptionCount)"
Write-Output "osvGated=$($report.osv.gatedVulnerabilityCount)"
Write-Output "report=$reportPath"
if (-not $passed) { exit 1 }
