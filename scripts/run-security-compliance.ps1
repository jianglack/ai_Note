param(
  [string]$JavaPath = "D:\Java\jdk17\bin\java.exe",
  [string]$BackendJar = "",
  [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$RepoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($BackendJar)) {
  $BackendJar = Join-Path $RepoRoot "backend\target\backend-0.1.0.jar"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $RepoRoot "backend\target\security-compliance"
}
$BackendJar = [IO.Path]::GetFullPath($BackendJar)
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)

$startedAt = [DateTimeOffset]::UtcNow
$runId = "ainote-security-$($startedAt.ToString('yyyyMMddTHHmmssZ'))-$PID"
$runDirectory = Join-Path $OutputRoot $runId
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$postgresName = "$runId-pg"
$redisName = "$runId-redis"
$containers = @($postgresName, $redisName)
$backendProcess = $null
$cleanupPassed = $false
$fatalError = $null
$tests = [System.Collections.Generic.List[object]]::new()

function Get-FreeTcpPort {
  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
  $listener.Start()
  try { return ([Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function New-RandomBase64([int]$length) {
  $bytes = New-Object byte[] $length
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  return [Convert]::ToBase64String($bytes)
}

function Set-ProcessEnvironment([hashtable]$values) {
  $previous = @{}
  foreach ($name in $values.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    [Environment]::SetEnvironmentVariable($name, [string]$values[$name], "Process")
  }
  return $previous
}

function Restore-ProcessEnvironment([hashtable]$previous) {
  foreach ($name in $previous.Keys) {
    [Environment]::SetEnvironmentVariable($name, $previous[$name], "Process")
  }
}

function Wait-Container([string]$name, [string[]]$command, [string]$expected) {
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $output = @(& docker exec $name @command 2>$null)
    if ($LASTEXITCODE -eq 0 -and ($output -join "`n") -match $expected) { return }
    Start-Sleep -Seconds 1
  }
  throw "Disposable container did not become ready: $name"
}

function Wait-Backend([string]$url, [Diagnostics.Process]$process) {
  for ($attempt = 0; $attempt -lt 180; $attempt++) {
    if ($process.HasExited) { throw "Security backend exited with code $($process.ExitCode)" }
    try {
      $health = Invoke-RestMethod -Uri "$url/actuator/health" -TimeoutSec 3
      if ($health.status -eq "UP") { return }
    } catch { }
    Start-Sleep -Seconds 1
  }
  throw "Security backend did not become healthy"
}

function New-ApiClient {
  $handler = [System.Net.Http.HttpClientHandler]::new()
  $handler.CookieContainer = [Net.CookieContainer]::new()
  $client = [System.Net.Http.HttpClient]::new($handler)
  $client.Timeout = [TimeSpan]::FromSeconds(20)
  return [pscustomobject]@{ Handler = $handler; Client = $client }
}

function Send-ApiRequest {
  param(
    [Parameter(Mandatory)]$ApiClient,
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$Method,
    [Parameter(Mandatory)][string]$Path,
    [hashtable]$Headers = @{},
    [AllowNull()][string]$Body = $null
  )
  $request = [System.Net.Http.HttpRequestMessage]::new(
    [System.Net.Http.HttpMethod]::new($Method), "$BaseUrl$Path")
  try {
    foreach ($name in $Headers.Keys) {
      [void]$request.Headers.TryAddWithoutValidation($name, [string]$Headers[$name])
    }
    if (-not [string]::IsNullOrEmpty($Body)) {
      $request.Content = [System.Net.Http.StringContent]::new(
        $Body, [Text.Encoding]::UTF8, "application/json")
    }
    $response = $ApiClient.Client.SendAsync($request).GetAwaiter().GetResult()
    try {
      $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
      $responseHeaders = @{}
      foreach ($header in $response.Headers) { $responseHeaders[$header.Key] = $header.Value -join ", " }
      foreach ($header in $response.Content.Headers) { $responseHeaders[$header.Key] = $header.Value -join ", " }
      return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Content = $content
        Headers = $responseHeaders
      }
    } finally { $response.Dispose() }
  } finally { $request.Dispose() }
}

function Get-CsrfToken($client, [string]$baseUrl) {
  $response = Send-ApiRequest $client $baseUrl "GET" "/api/auth/csrf"
  if ($response.Status -ne 200) { throw "CSRF bootstrap failed with $($response.Status)" }
  return ($response.Content | ConvertFrom-Json).token
}

function Add-Test([string]$name, [bool]$passed, [string]$expected, [string]$actual, [string]$evidence) {
  $tests.Add([ordered]@{
    name = $name
    status = $(if ($passed) { "PASSED" } else { "FAILED" })
    expected = $expected
    actual = $actual
    evidence = $evidence
  })
}

function Assert-Status([string]$name, $response, [int[]]$expected, [string]$evidence) {
  $passed = $expected -contains $response.Status
  Add-Test $name $passed ($expected -join "/") ([string]$response.Status) $evidence
}

function Remove-SecurityContainer([string]$name) {
  if ($name -notmatch '^ainote-security-') { throw "Refusing to remove non-security container: $name" }
  & docker rm -f $name *> $null
}

try {
  if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) { throw "Java 17 not found: $JavaPath" }
  if (-not (Test-Path -LiteralPath $BackendJar -PathType Leaf)) { throw "Backend jar not found: $BackendJar" }
  & docker version --format "{{.Server.Version}}" *> $null
  if ($LASTEXITCODE -ne 0) { throw "Docker is unavailable" }

  $databasePassword = New-RandomBase64 24
  & docker run -d --name $postgresName -e "POSTGRES_USER=ainote" -e "POSTGRES_PASSWORD=$databasePassword" `
    -e "POSTGRES_DB=ainote_security" -p "127.0.0.1::5432" pgvector/pgvector:pg15 *> $null
  if ($LASTEXITCODE -ne 0) { throw "Failed to start disposable PostgreSQL" }
  & docker run -d --name $redisName -p "127.0.0.1::6379" redis:7-alpine *> $null
  if ($LASTEXITCODE -ne 0) { throw "Failed to start disposable Redis" }
  Wait-Container $postgresName @("pg_isready", "-U", "ainote", "-d", "ainote_security") "accepting connections"
  Wait-Container $redisName @("redis-cli", "ping") "PONG"

  $postgresPort = ((& docker port $postgresName "5432/tcp") -split ':')[-1].Trim()
  $redisPort = ((& docker port $redisName "6379/tcp") -split ':')[-1].Trim()
  $backendPort = Get-FreeTcpPort
  $baseUrl = "http://127.0.0.1:$backendPort"
  $environment = @{
    SPRING_PROFILES_ACTIVE = "prod"
    SERVER_PORT = $backendPort
    SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$postgresPort/ainote_security?stringtype=unspecified"
    SPRING_DATASOURCE_USERNAME = "ainote"
    SPRING_DATASOURCE_PASSWORD = $databasePassword
    SPRING_DATA_REDIS_HOST = "127.0.0.1"
    SPRING_DATA_REDIS_PORT = $redisPort
    NEO4J_ENABLED = "false"
    JWT_SECRET = New-RandomBase64 48
    DEEPSEEK_API_KEY = "managed-local-no-model-call"
    AUTH_COOKIE_SECURE = "false"
    APP_CORS_ALLOWED_ORIGINS = "http://127.0.0.1:5173"
    MEMORY_CHAT_HISTORY_COMPACTION_ENABLED = "false"
  }
  $previousEnvironment = Set-ProcessEnvironment $environment
  try {
    $backendProcess = Start-Process -FilePath $JavaPath `
      -ArgumentList @("-Xms256m", "-Xmx768m", "-jar", $BackendJar) `
      -WorkingDirectory (Split-Path -Parent $BackendJar) -WindowStyle Hidden `
      -RedirectStandardOutput (Join-Path $runDirectory "backend.out.log") `
      -RedirectStandardError (Join-Path $runDirectory "backend.err.log") -PassThru
  } finally { Restore-ProcessEnvironment $previousEnvironment }
  Wait-Backend $baseUrl $backendProcess

  $anonymous = New-ApiClient
  $userA = New-ApiClient
  $userB = New-ApiClient
  $bearerClient = New-ApiClient
  $rateClient = New-ApiClient
  try {
    $health = Send-ApiRequest $anonymous $baseUrl "GET" "/actuator/health"
    Assert-Status "public_health" $health @(200) "Production health is reachable."
    $unauthorized = Send-ApiRequest $anonymous $baseUrl "GET" "/api/notes"
    Assert-Status "protected_api_requires_authentication" $unauthorized @(401) "Anonymous note access is denied."

    $csrfA = Get-CsrfToken $userA $baseUrl
    $csrfB = Get-CsrfToken $userB $baseUrl
    $passwordA = "A!" + [Guid]::NewGuid().ToString("N")
    $passwordB = "B!" + [Guid]::NewGuid().ToString("N")
    $registerA = Send-ApiRequest $userA $baseUrl "POST" "/api/auth/register" `
      @{ "X-XSRF-TOKEN" = $csrfA } (@{username="security-a";email="security-a@example.invalid";password=$passwordA}|ConvertTo-Json -Compress)
    $registerB = Send-ApiRequest $userB $baseUrl "POST" "/api/auth/register" `
      @{ "X-XSRF-TOKEN" = $csrfB } (@{username="security-b";email="security-b@example.invalid";password=$passwordB}|ConvertTo-Json -Compress)
    Assert-Status "register_user_a" $registerA @(200) "Disposable ordinary user created."
    Assert-Status "register_user_b" $registerB @(200) "Second tenant created."
    $csrfA = Get-CsrfToken $userA $baseUrl
    $csrfB = Get-CsrfToken $userB $baseUrl
    Add-Test "jwt_absent_from_auth_json" ($registerA.Content -notmatch '"token"') "No token field" `
      $(if ($registerA.Content -match '"token"') { "token field present" } else { "token field absent" }) "JWT is not exposed to browser JavaScript."
    $setCookie = [string]$registerA.Headers["Set-Cookie"]
    $cookieFlagsPassed = $setCookie -match 'AINOTE_AUTH=' -and $setCookie -match 'HttpOnly' -and $setCookie -match 'SameSite=Lax'
    Add-Test "auth_cookie_flags" $cookieFlagsPassed "HttpOnly and SameSite=Lax" `
      $(if ($cookieFlagsPassed) { "required local flags present" } else { "required flags missing" }) `
      "Secure is statically required by the production profile and disabled only for this HTTP-only local runner."

    $csrfDenied = Send-ApiRequest $userA $baseUrl "POST" "/api/notes" @{} `
      (@{title="csrf-negative";content="not persisted"}|ConvertTo-Json -Compress)
    Assert-Status "csrf_missing_rejected" $csrfDenied @(401,403) "Cookie-authenticated unsafe request has no CSRF header."

    $created = Send-ApiRequest $userA $baseUrl "POST" "/api/notes" @{"X-XSRF-TOKEN"=$csrfA} `
      (@{title="tenant-a-fixture";content="managed-local security fixture"}|ConvertTo-Json -Compress)
    Assert-Status "csrf_valid_request_accepted" $created @(200) "Valid double-submit CSRF request is accepted."
    $noteId = $null
    if ($created.Status -eq 200) { $noteId = ($created.Content | ConvertFrom-Json).id }
    if ($noteId) {
      $bolaRead = Send-ApiRequest $userB $baseUrl "GET" "/api/notes/$noteId"
      $csrfB = Get-CsrfToken $userB $baseUrl
      $bolaUpdate = Send-ApiRequest $userB $baseUrl "PUT" "/api/notes/$noteId" @{"X-XSRF-TOKEN"=$csrfB} `
        (@{title="cross-tenant";content="denied"}|ConvertTo-Json -Compress)
      $csrfB = Get-CsrfToken $userB $baseUrl
      $bolaDelete = Send-ApiRequest $userB $baseUrl "DELETE" "/api/notes/$noteId" @{"X-XSRF-TOKEN"=$csrfB}
      Assert-Status "bola_cross_tenant_read_denied" $bolaRead @(404) "Ownership is not disclosed."
      Assert-Status "bola_cross_tenant_update_denied" $bolaUpdate @(404) "Foreign tenant update is denied."
      Assert-Status "bola_cross_tenant_delete_denied" $bolaDelete @(204,404) "Delete must not affect a foreign tenant object."
      $ownerStillReads = Send-ApiRequest $userA $baseUrl "GET" "/api/notes/$noteId"
      Assert-Status "bola_delete_did_not_modify_owner_object" $ownerStillReads @(200) "Owner object remains after foreign delete attempt."
    } else {
      Add-Test "bola_fixture_created" $false "note id" "missing" "BOLA probes could not run."
    }

    $adminDenied = Send-ApiRequest $userA $baseUrl "GET" "/api/admin/agent-metrics"
    Assert-Status "ordinary_user_admin_denied" $adminDenied @(403) "Admin allow-list fails closed."

    $csrfA = Get-CsrfToken $userA $baseUrl
    $malformed = Send-ApiRequest $userA $baseUrl "POST" "/api/notes" @{"X-XSRF-TOKEN"=$csrfA} '{bad-json'
    Assert-Status "malformed_json_rejected" $malformed @(400) "Malformed JSON is rejected."
    Add-Test "generic_error_has_no_stack_trace" ($malformed.Content -notmatch 'Exception|at com\.ainote|stackTrace') `
      "No implementation detail" "sanitized response" "Error response is generic."

    $canaryPort = Get-FreeTcpPort
    $canary = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $canaryPort)
    $canary.Start()
    try {
      $pendingAccept = $canary.BeginAcceptTcpClient($null, $null)
      $encodedUrl = [Uri]::EscapeDataString("http://127.0.0.1:$canaryPort/internal")
      $ssrf = Send-ApiRequest $userA $baseUrl "GET" "/api/link-preview?url=$encodedUrl"
      Start-Sleep -Milliseconds 200
      $noConnection = -not $pendingAccept.IsCompleted
      Add-Test "ssrf_loopback_no_outbound_connection" ($ssrf.Status -eq 200 -and $noConnection) `
        "No canary connection" $(if ($noConnection) { "no connection" } else { "connection observed" }) `
        "A loopback canary proves the backend did not connect."
      if ($pendingAccept.IsCompleted) { $canary.EndAcceptTcpClient($pendingAccept).Dispose() }
    } finally { $canary.Stop() }

    $badCors = Send-ApiRequest $anonymous $baseUrl "OPTIONS" "/api/notes" `
      @{Origin="https://evil.example";"Access-Control-Request-Method"="GET"}
    Add-Test "cors_disallowed_origin_rejected" `
      (-not $badCors.Headers.ContainsKey("Access-Control-Allow-Origin")) "No allow-origin header" `
      $(if ($badCors.Headers.ContainsKey("Access-Control-Allow-Origin")) { "header present" } else { "header absent" }) `
      "Unconfigured origins receive no browser permission."
    $trace = Send-ApiRequest $anonymous $baseUrl "TRACE" "/api/notes"
    Assert-Status "trace_method_rejected" $trace @(400,401,403,405) "TRACE does not execute application behavior."
    $swagger = Send-ApiRequest $anonymous $baseUrl "GET" "/v3/api-docs"
    Assert-Status "production_swagger_unavailable" $swagger @(404) "Production profile disables API documentation."
    $actuator = Send-ApiRequest $anonymous $baseUrl "GET" "/actuator/env"
    Assert-Status "non_health_actuator_unavailable" $actuator @(401,404) "Only health is exposed."
    $requiredHeaders = @("Content-Security-Policy", "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy")
    $missingHeaders = @($requiredHeaders | Where-Object { -not $health.Headers.ContainsKey($_) })
    Add-Test "backend_security_headers" ($missingHeaders.Count -eq 0) ($requiredHeaders -join ",") `
      $(if ($missingHeaders.Count -eq 0) { "all present" } else { "missing: " + ($missingHeaders -join ",") }) `
      "Headers sampled from the production-profile backend."

    $authCookie = $userA.Handler.CookieContainer.GetCookies([Uri]$baseUrl)["AINOTE_AUTH"]
    if ($null -ne $authCookie) {
      $bearer = $authCookie.Value
      $bearerMe = Send-ApiRequest $bearerClient $baseUrl "GET" "/api/auth/me" @{Authorization="Bearer $bearer"}
      Assert-Status "explicit_bearer_client_supported" $bearerMe @(200) "Non-browser API authentication remains supported."
      $csrfA = Get-CsrfToken $userA $baseUrl
      $logout = Send-ApiRequest $userA $baseUrl "POST" "/api/auth/logout" @{"X-XSRF-TOKEN"=$csrfA}
      Assert-Status "logout_cookie_request" $logout @(200) "Logout accepted."
      $revoked = Send-ApiRequest $bearerClient $baseUrl "GET" "/api/auth/me" @{Authorization="Bearer $bearer"}
      Assert-Status "logout_revokes_server_token" $revoked @(401) "Previously valid bearer is rejected after logout."
      $bearer = $null
    } else {
      Add-Test "auth_cookie_available_for_revocation_probe" $false "auth cookie" "missing" "No token value is written to evidence."
    }

    $rateCsrf = Get-CsrfToken $rateClient $baseUrl
    $statuses = [System.Collections.Generic.List[int]]::new()
    for ($attempt = 1; $attempt -le 11; $attempt++) {
      $failedLogin = Send-ApiRequest $rateClient $baseUrl "POST" "/api/auth/login" @{"X-XSRF-TOKEN"=$rateCsrf} `
        (@{username="rate-limit-subject";password="incorrect-password"}|ConvertTo-Json -Compress)
      $statuses.Add($failedLogin.Status)
    }
    Add-Test "login_rate_limit" ($statuses[10] -eq 429) "11th request is 429" ([string]$statuses[10]) `
      "Only status codes are retained; credentials are excluded."
  } finally {
    foreach ($client in @($anonymous, $userA, $userB, $bearerClient, $rateClient)) {
      $client.Client.Dispose()
      $client.Handler.Dispose()
    }
  }
} catch {
  $fatalError = $_.Exception.Message
} finally {
  if ($null -ne $backendProcess -and -not $backendProcess.HasExited) {
    Stop-Process -Id $backendProcess.Id -Force
    $backendProcess.WaitForExit(15000) | Out-Null
  }
  $cleanupErrors = [System.Collections.Generic.List[string]]::new()
  foreach ($container in $containers) {
    try { Remove-SecurityContainer $container } catch { $cleanupErrors.Add($_.Exception.Message) }
  }
  $cleanupPassed = $cleanupErrors.Count -eq 0
  if (-not $cleanupPassed -and $null -eq $fatalError) { $fatalError = $cleanupErrors -join "; " }
}

$failedTests = @($tests | Where-Object { $_.status -ne "PASSED" })
$qualityGatePassed = $null -eq $fatalError -and $failedTests.Count -eq 0 -and $cleanupPassed
$report = [ordered]@{
  schemaVersion = "security-compliance-v1"
  status = $(if ($qualityGatePassed) { "PASSED" } else { "FAILED" })
  qualityGatePassed = $qualityGatePassed
  evidenceClass = "managed-local"
  productionProven = $false
  startedAt = $startedAt.ToString("o")
  finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
  baselines = [ordered]@{
    owaspAsvs = "5.0.0 Level 2-inspired scoped subset"
    owaspApiSecurity = "2023"
    nistSsdf = "1.1"
    nistDigitalIdentity = "SP 800-63B-4"
  }
  localExceptions = @("AUTH_COOKIE_SECURE=false only because this disposable runner uses loopback HTTP")
  testCount = $tests.Count
  failureCount = $failedTests.Count + $(if ($null -ne $fatalError) { 1 } else { 0 })
  tests = $tests
  fatalError = $fatalError
  cleanupPassed = $cleanupPassed
  nonClaims = @(
    "No independent third-party penetration-test certificate",
    "No internet-edge, cloud IAM, KMS/HSM, host, WAF, or DDoS proof",
    "No legal or regulatory certification"
  )
}
$reportPath = Join-Path $runDirectory "security-compliance-report.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Output "status=$($report.status)"
Write-Output "qualityGatePassed=$qualityGatePassed"
Write-Output "testCount=$($tests.Count)"
Write-Output "failureCount=$($report.failureCount)"
Write-Output "cleanupPassed=$cleanupPassed"
Write-Output "report=$reportPath"
if (-not $qualityGatePassed) { exit 1 }
