param(
  [string]$BaseUrl = "http://127.0.0.1:8081",
  [string]$EmbeddingUrl = "http://127.0.0.1:8082/v1"
)

$ErrorActionPreference = "Stop"

$runDir = Join-Path $env:TEMP "ainote-real-run"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User"))) {
  throw "DEEPSEEK_API_KEY is missing from the Windows User environment"
}

$embeddingBody = @{
  input = "hello semantic vector"
  model = "Qwen/Qwen3-Embedding-0.6B"
  encoding_format = "float"
} | ConvertTo-Json -Compress

$embedding = Invoke-RestMethod `
  -Uri "$EmbeddingUrl/embeddings" `
  -Method Post `
  -ContentType "application/json" `
  -Body $embeddingBody `
  -TimeoutSec 60

$dimension = $embedding.data[0].embedding.Count
if ($dimension -ne 1024) {
  throw "Expected 1024 embedding dimensions but got $dimension"
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$username = "real_ai_$stamp"
$email = "$username@example.test"
$password = "CodexReal123!"

$authBody = @{
  username = $username
  email = $email
  password = $password
} | ConvertTo-Json -Compress

$auth = Invoke-RestMethod `
  -Uri "$BaseUrl/api/auth/register" `
  -Method Post `
  -ContentType "application/json" `
  -Body $authBody `
  -TimeoutSec 60

if ([string]::IsNullOrWhiteSpace($auth.token)) {
  throw "Register returned an empty token"
}

$headers = @{ Authorization = "Bearer $($auth.token)" }
$noteBody = @{
  title = "Real AI smoke $stamp"
  content = "Verify DeepSeek chat and local TEI embedding write-through. Smoke id $stamp"
  tags = @("real-ai-smoke")
} | ConvertTo-Json -Compress

$note = Invoke-RestMethod `
  -Uri "$BaseUrl/api/notes" `
  -Method Post `
  -ContentType "application/json" `
  -Headers $headers `
  -Body $noteBody `
  -TimeoutSec 60

$chatBody = @{
  query = "Reply with the exact phrase: real AI smoke OK"
  message = "Reply with the exact phrase: real AI smoke OK"
  scope = "selected"
  noteIds = @($note.id)
} | ConvertTo-Json -Compress

$chat = Invoke-RestMethod `
  -Uri "$BaseUrl/api/ai/chat" `
  -Method Post `
  -ContentType "application/json" `
  -Headers $headers `
  -Body $chatBody `
  -TimeoutSec 180

$text = [string]$chat.content
if ($text -notmatch "real AI smoke OK") {
  throw "Unexpected chat response: $text"
}

Set-Content -Path (Join-Path $runDir "token.txt") -Value $auth.token
Set-Content -Path (Join-Path $runDir "user-id.txt") -Value $auth.userId
Set-Content -Path (Join-Path $runDir "note.txt") -Value $note.id
Set-Content -Path (Join-Path $runDir "username.txt") -Value $username
Set-Content -Path (Join-Path $runDir "password.txt") -Value $password
Write-Output "real-ai-smoke=passed"
Write-Output "embedding-dimension=$dimension"
Write-Output "note-id=$($note.id)"
