param(
  [string]$BaseUrl = "http://127.0.0.1:8081",
  [string]$EmbeddingUrl = "http://127.0.0.1:8082/v1",
  [string]$EmbeddingModel = "Qwen/Qwen3-Embedding-0.6B",
  [double]$EmbeddingP95Ms = 4500,
  [string]$DockerHost = "",
  [string]$DockerApiVersion = "",
  [string]$DockerTlsVerify = "",
  [double]$SseThreadLeakMaxDelta = 20,
  [switch]$RunSchedulerSoak,
  [string]$SchedulerSoakDuration = "30m",
  [switch]$SkipLlmPerf,
  [switch]$SkipFullstackE2E,
  [switch]$SkipFrontendCoverage
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RepoRoot "backend"
$FrontendDir = Join-Path $RepoRoot "frontend"
$PerfScript = Join-Path $RepoRoot "perf\ainote-load.js"
$RealAiScript = Join-Path $RepoRoot "scripts\verify-real-ai.ps1"
$SeedPerfNotesScript = Join-Path $RepoRoot "scripts\seed-perf-notes.ps1"

function Require-Command {
  param([string]$Name)

  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command not found: $Name"
  }
}

function Resolve-K6 {
  $cmd = Get-Command k6 -ErrorAction SilentlyContinue
  if ($cmd) {
    return $cmd.Source
  }

  $defaultPath = "C:\Program Files\k6\k6.exe"
  if (Test-Path $defaultPath) {
    return $defaultPath
  }

  throw "Required command not found: k6"
}

function Invoke-Step {
  param(
    [string]$Name,
    [scriptblock]$Action
  )

  Write-Output "==> $Name"
  & $Action
  Write-Output "<== $Name"
}

function Invoke-Native {
  param(
    [string]$FilePath,
    [string[]]$Arguments = @()
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
  }
}

function Start-TempPgvector {
  $name = "ainote-it-pg-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$PID"
  Invoke-Native "docker" @(
    "run", "-d",
    "--name", $name,
    "-e", "POSTGRES_USER=ainote",
    "-e", "POSTGRES_PASSWORD=ainote",
    "-e", "POSTGRES_DB=postgres",
    "-p", "127.0.0.1::5432",
    "pgvector/pgvector:pg15"
  )

  $ready = $false
  for ($i = 0; $i -lt 60; $i++) {
    & docker exec $name pg_isready -U ainote -d postgres | Out-Null
    if ($LASTEXITCODE -eq 0) {
      $ready = $true
      break
    }
    Start-Sleep -Seconds 1
  }
  if (-not $ready) {
    docker logs $name | Out-Host
    throw "Temporary pgvector container did not become ready: $name"
  }

  $portLine = (& docker port $name 5432/tcp)
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($portLine)) {
    throw "Could not resolve temporary pgvector port for $name"
  }
  $port = ($portLine -split ":")[-1].Trim()
  [pscustomobject]@{
    Name = $name
    AdminUrl = "jdbc:postgresql://127.0.0.1:$port/postgres"
    Username = "ainote"
    Password = "ainote"
  }
}

function Stop-TempPgvector {
  param([object]$Container)

  if ($null -ne $Container -and -not [string]::IsNullOrWhiteSpace($Container.Name)) {
    & docker rm -f $Container.Name | Out-Null
  }
}

function Assert-NoSkippedMavenTests {
  param([string]$BackendDir)

  $files = @()
  $files += Get-ChildItem (Join-Path $BackendDir "target\surefire-reports\TEST-*.xml") -ErrorAction SilentlyContinue
  $files += Get-ChildItem (Join-Path $BackendDir "target\failsafe-reports\TEST-*.xml") -ErrorAction SilentlyContinue
  $skipped = @()
  foreach ($file in $files) {
    [xml]$xml = Get-Content -LiteralPath $file.FullName
    foreach ($case in $xml.testsuite.testcase) {
      if ($case.skipped) {
        $skipped += "$($case.classname).$($case.name): $($case.skipped.message)"
      }
    }
  }
  if ($skipped.Count -gt 0) {
    throw "Maven verification had skipped tests:`n$($skipped -join "`n")"
  }
}

function Get-ActuatorMetricValue {
  param(
    [string]$BaseUrl,
    [string]$Token,
    [string]$Name,
    [string]$Statistic = "VALUE"
  )

  $metric = Invoke-RestMethod `
    -Uri "$BaseUrl/actuator/metrics/$Name" `
    -Headers @{ Authorization = "Bearer $Token" } `
    -TimeoutSec 15
  $measurement = $metric.measurements | Where-Object { $_.statistic -eq $Statistic } | Select-Object -First 1
  if ($null -eq $measurement) {
    throw "Metric $Name did not contain statistic $Statistic"
  }
  [double]$measurement.value
}

function Assert-MetricDelta {
  param(
    [string]$MetricName,
    [double]$Before,
    [double]$After,
    [double]$MaxDelta
  )

  $delta = $After - $Before
  Write-Output "$MetricName-before=$Before after=$After delta=$delta max-delta=$MaxDelta"
  if ($delta -gt $MaxDelta) {
    throw "$MetricName increased by $delta, above allowed delta $MaxDelta"
  }
}

Require-Command docker
Require-Command mvn
Require-Command npm
$K6 = Resolve-K6

Invoke-Step "docker availability" {
  Invoke-Native "docker" @("version", "--format", "docker-server={{.Server.Version}}")
  Invoke-Native "docker" @("ps", "--format", "container={{.Names}} status={{.Status}}")
}

Invoke-Step "backend health" {
  Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 15 | Out-Null
}

Invoke-Step "backend maven verify" {
  $tempPg = $null
  $mvnArgs = @("-q", "-o", "clean", "verify")
  if (-not [string]::IsNullOrWhiteSpace($DockerHost)) {
    $mvnArgs += "-Ddocker.host=$DockerHost"
  }
  if (-not [string]::IsNullOrWhiteSpace($DockerApiVersion)) {
    $mvnArgs += "-Dapi.version=$DockerApiVersion"
  }
  if (-not [string]::IsNullOrWhiteSpace($DockerTlsVerify)) {
    $mvnArgs += "-Ddocker.tls.verify=$DockerTlsVerify"
  }

  Push-Location $BackendDir
  try {
    $tempPg = Start-TempPgvector
    $env:AINOTE_IT_TEMP_DB = "true"
    $env:AINOTE_IT_JDBC_ADMIN_URL = $tempPg.AdminUrl
    $env:AINOTE_IT_JDBC_USERNAME = $tempPg.Username
    $env:AINOTE_IT_JDBC_PASSWORD = $tempPg.Password
    Invoke-Native "mvn" $mvnArgs
    Assert-NoSkippedMavenTests $BackendDir
  } finally {
    Remove-Item Env:\AINOTE_IT_TEMP_DB -ErrorAction SilentlyContinue
    Remove-Item Env:\AINOTE_IT_JDBC_ADMIN_URL -ErrorAction SilentlyContinue
    Remove-Item Env:\AINOTE_IT_JDBC_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\AINOTE_IT_JDBC_PASSWORD -ErrorAction SilentlyContinue
    Stop-TempPgvector $tempPg
    Pop-Location
  }
}

Invoke-Step "frontend vitest" {
  Push-Location $FrontendDir
  try {
    Invoke-Native "npm" @("test")
  } finally {
    Pop-Location
  }
}

if (-not $SkipFrontendCoverage) {
  Invoke-Step "frontend coverage" {
    Push-Location $FrontendDir
    try {
      Invoke-Native "npm" @("run", "test:coverage")
    } finally {
      Pop-Location
    }
  }
}

Invoke-Step "frontend build" {
  Push-Location $FrontendDir
  try {
    Invoke-Native "npm" @("run", "build")
  } finally {
    Pop-Location
  }
}

Invoke-Step "real ai smoke" {
  Invoke-Native "powershell" @("-ExecutionPolicy", "Bypass", "-File", $RealAiScript, "-BaseUrl", $BaseUrl, "-EmbeddingUrl", $EmbeddingUrl)
}

Invoke-Step "mocked playwright e2e" {
  Push-Location $FrontendDir
  try {
    Invoke-Native "npm" @("run", "test:e2e", "--", "e2e/ainote.spec.ts")
  } finally {
    Pop-Location
  }
}

if (-not $SkipFullstackE2E) {
  Invoke-Step "fullstack playwright e2e" {
    Push-Location $FrontendDir
    try {
      $env:AINOTE_E2E_FULLSTACK = "true"
      $env:AINOTE_BACKEND_URL = $BaseUrl
      Invoke-Native "npm" @("run", "test:e2e", "--", "e2e/fullstack.spec.ts")
    } finally {
      Remove-Item Env:\AINOTE_E2E_FULLSTACK -ErrorAction SilentlyContinue
      Remove-Item Env:\AINOTE_BACKEND_URL -ErrorAction SilentlyContinue
      Pop-Location
    }
  }
}

Invoke-Step "k6 local core profile" {
  $perfUserId = (Get-Content "$env:TEMP\ainote-real-run\user-id.txt" -Raw).Trim()
  Invoke-Native "powershell" @(
    "-ExecutionPolicy", "Bypass",
    "-File", $SeedPerfNotesScript,
    "-UserId", $perfUserId,
    "-Count", "10000"
  )
  try {
    $env:BASE_URL = $BaseUrl
    $env:AUTH_TOKEN = (Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
    $env:PERF_PROFILE = "local_core"
    $env:PERF_MIN_NOTES = "10000"
    $env:DURATION = "30s"
    Invoke-Native $K6 @("run", $PerfScript)
  } finally {
    Remove-Item Env:\BASE_URL,Env:\AUTH_TOKEN,Env:\PERF_PROFILE,Env:\PERF_MIN_NOTES,Env:\DURATION -ErrorAction SilentlyContinue
    Invoke-Native "powershell" @(
      "-ExecutionPolicy", "Bypass",
      "-File", $SeedPerfNotesScript,
      "-UserId", $perfUserId,
      "-Cleanup"
    )
  }
}

Invoke-Step "k6 embedding direct profile" {
  $env:EMBEDDING_URL = $EmbeddingUrl
  $env:EMBEDDING_MODEL = $EmbeddingModel
  $env:EMBEDDING_P95_MS = [string]$EmbeddingP95Ms
  $env:PERF_PROFILE = "embedding_direct"
  $env:DURATION = "30s"
  $env:EMBEDDING_VUS = "1"
  try {
    Invoke-Native $K6 @("run", $PerfScript)
  } finally {
    Remove-Item Env:\EMBEDDING_URL,Env:\EMBEDDING_MODEL,Env:\EMBEDDING_P95_MS,Env:\PERF_PROFILE,Env:\DURATION,Env:\EMBEDDING_VUS -ErrorAction SilentlyContinue
  }
}

if ($RunSchedulerSoak) {
  Invoke-Step "k6 scheduler soak profile" {
    $env:BASE_URL = $BaseUrl
    $env:AUTH_TOKEN = (Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
    $env:PERF_PROFILE = "scheduler_soak"
    $env:DURATION = $SchedulerSoakDuration
    try {
      Invoke-Native $K6 @("run", $PerfScript)
    } finally {
      Remove-Item Env:\BASE_URL,Env:\AUTH_TOKEN,Env:\PERF_PROFILE,Env:\DURATION -ErrorAction SilentlyContinue
    }
  }
}

Invoke-Step "k6 sse 100 profile" {
  $token = (Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
  $env:BASE_URL = $BaseUrl
  $env:AUTH_TOKEN = $token
  $env:PERF_PROFILE = "sse_100"
  $env:SSE_VUS = "100"
  try {
    $env:DURATION = "5s"
    Invoke-Native $K6 @("run", $PerfScript)
    Start-Sleep -Seconds 5
    $threadsBefore = Get-ActuatorMetricValue -BaseUrl $BaseUrl -Token $token -Name "jvm.threads.live"
    $env:DURATION = "30s"
    Invoke-Native $K6 @("run", $PerfScript)
  } finally {
    Remove-Item Env:\BASE_URL,Env:\AUTH_TOKEN,Env:\PERF_PROFILE,Env:\DURATION,Env:\SSE_VUS -ErrorAction SilentlyContinue
  }
  Start-Sleep -Seconds 10
  $threadsAfter = Get-ActuatorMetricValue -BaseUrl $BaseUrl -Token $token -Name "jvm.threads.live"
  Assert-MetricDelta -MetricName "jvm.threads.live" -Before $threadsBefore -After $threadsAfter -MaxDelta $SseThreadLeakMaxDelta
}

if (-not $SkipLlmPerf) {
  Invoke-Step "k6 llm profile" {
    $env:BASE_URL = $BaseUrl
    $env:AUTH_TOKEN = (Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
    $env:PERF_PROFILE = "llm"
    $env:DURATION = "30s"
    $env:SSE_VUS = "10"
    try {
      Invoke-Native $K6 @("run", $PerfScript)
    } finally {
      Remove-Item Env:\BASE_URL,Env:\AUTH_TOKEN,Env:\PERF_PROFILE,Env:\DURATION,Env:\SSE_VUS -ErrorAction SilentlyContinue
    }
  }

  Invoke-Step "k6 agent saturation profile" {
    $env:BASE_URL = $BaseUrl
    $env:AUTH_TOKEN = (Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
    $env:PERF_PROFILE = "agent_saturation"
    $env:DURATION = "30s"
    $env:AGENT_SATURATION_VUS = "16"
    try {
      Invoke-Native $K6 @("run", $PerfScript)
    } finally {
      Remove-Item Env:\BASE_URL,Env:\AUTH_TOKEN,Env:\PERF_PROFILE,Env:\DURATION,Env:\AGENT_SATURATION_VUS -ErrorAction SilentlyContinue
    }
  }
}

Invoke-Step "k6 business api profile" {
  $env:BASE_URL = $BaseUrl
  $env:AUTH_TOKEN = (Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
  $env:BUSINESS_USERNAME = (Get-Content "$env:TEMP\ainote-real-run\username.txt" -Raw).Trim()
  $env:BUSINESS_PASSWORD = (Get-Content "$env:TEMP\ainote-real-run\password.txt" -Raw).Trim()
  $env:PERF_PROFILE = "business_api"
  $env:DURATION = "30s"
  $env:BUSINESS_VUS = "2"
  try {
    Invoke-Native $K6 @("run", $PerfScript)
  } finally {
    Remove-Item Env:\BASE_URL,Env:\AUTH_TOKEN,Env:\BUSINESS_USERNAME,Env:\BUSINESS_PASSWORD,Env:\PERF_PROFILE,Env:\DURATION,Env:\BUSINESS_VUS -ErrorAction SilentlyContinue
  }
}

Write-Output "release-verification=passed"
