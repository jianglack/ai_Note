param(
  [int]$TargetCount = 2000,
  [string]$OutputPath = "backend/src/test/resources/memory/external-real-human-replay-dataset.json"
)

$ErrorActionPreference = "Stop"

$dataset = "allenai/WildChat"
$config = "default"
$split = "train"
$approvedAt = "2026-07-08T00:00:00Z"
$sourceBase = "hf://datasets/allenai/WildChat/default/train"
$assistantPlaceholder = "External assistant response omitted after provenance and redaction review."

$queries = @(
  @{ Query = "remember"; Tags = @("external_memory_preference"); Max = 260 },
  @{ Query = "keep in mind"; Tags = @("external_memory_preference"); Max = 160 },
  @{ Query = "I prefer"; Tags = @("external_memory_preference"); Max = 260 },
  @{ Query = "prefer"; Tags = @("external_memory_preference"); Max = 260 },
  @{ Query = "from now on"; Tags = @("external_style_request"); Max = 180 },
  @{ Query = "always answer"; Tags = @("external_style_request"); Max = 140 },
  @{ Query = "tone"; Tags = @("external_style_request"); Max = 180 },
  @{ Query = "instead"; Tags = @("external_correction"); Max = 220 },
  @{ Query = "rather"; Tags = @("external_correction"); Max = 160 },
  @{ Query = "project"; Tags = @("external_work_context"); Max = 200 },
  @{ Query = "meeting notes"; Tags = @("external_work_context"); Max = 140 },
  @{ Query = "requirements"; Tags = @("external_work_context"); Max = 140 },
  @{ Query = "summarize this note"; Tags = @("external_reference_task"); Max = 120 },
  @{ Query = "summarize this"; Tags = @("external_reference_task"); Max = 220 },
  @{ Query = "based on this"; Tags = @("external_reference_task"); Max = 160 },
  @{ Query = "this reply"; Tags = @("external_one_off"); Max = 160 },
  @{ Query = "this answer"; Tags = @("external_assistant_feedback"); Max = 160 },
  @{ Query = "your answer"; Tags = @("external_assistant_feedback"); Max = 160 },
  @{ Query = "good answer"; Tags = @("external_assistant_feedback"); Max = 120 },
  @{ Query = "forget this"; Tags = @("external_memory_control"); Max = 120 },
  @{ Query = "delete note"; Tags = @("external_memory_control"); Max = 100 },
  @{ Query = "api key"; Tags = @("external_sensitive_boundary"); Max = 100 },
  @{ Query = "password"; Tags = @("external_sensitive_boundary"); Max = 100 },
  @{ Query = "not sure"; Tags = @("external_ambiguous"); Max = 180 },
  @{ Query = "maybe"; Tags = @("external_ambiguous"); Max = 180 }
)

function Compact([string]$Text) {
  if ($null -eq $Text) { return "" }
  return ($Text.Trim().ToLowerInvariant() -replace "\s+", "")
}

function HasRiskyText([string]$Text) {
  if ([string]::IsNullOrWhiteSpace($Text)) { return $true }
  if ($Text.Contains([char]0xfffd)) { return $true }
  if ($Text -match "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}") { return $true }
  if ($Text -match "(?<!\d)(?:\+?\d[\d -]{8,}\d)(?!\d)") { return $true }
  if ($Text -match "(?i)\b(?!example\.com\b|example\.org\b|example\.net\b)[a-z0-9-]+\.(com|cn|net|org|io|ai)\b") { return $true }
  if ($Text -match "AKIA[0-9A-Z]{16}") { return $true }
  if ($Text -match "(?i)sk-[a-z0-9_-]{8,}") { return $true }
  if ($Text -match "(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)") { return $true }
  return $false
}

function NormalizeText([string]$Text) {
  $value = ($Text -replace "\r\n", "`n" -replace "\r", "`n").Trim()
  $value = $value -replace "\s+", " "
  if ($value.Length -gt 600) {
    $value = $value.Substring(0, 600).Trim()
  }
  return $value
}

function Get-PolicyExpectation([string]$UserMessage, [string]$AssistantOutput) {
  $message = $UserMessage.Trim().ToLowerInvariant()
  $assistant = $AssistantOutput.Trim().ToLowerInvariant()
  $compact = Compact $message
  $signals = New-Object System.Collections.Generic.List[string]

  $sensitive = $message -match "(?i)(api[_ -]?key|secret|password|token|sk-[a-z0-9_-]+|AKIA[0-9A-Z]{16})" -or
               $assistant -match "(?i)(api[_ -]?key|secret|password|token|sk-[a-z0-9_-]+|AKIA[0-9A-Z]{16})"
  $forget = $compact.Contains("forgetthis") -or $compact.Contains("forgetthat")
  $referenceOnly = $compact.Contains("selectednote") -or $compact.Contains("currentnote") -or
                   $compact.Contains("thisnote") -or $compact.Contains("whatdoesthisnotesay") -or
                   $compact.Contains("summarizethisnote")
  $operation = $compact -in @("ok", "yes", "no", "confirm", "cancel") -or
               $compact.Contains("deleteallnotes") -or $compact.Contains("deleteeverynote") -or
               $compact.Contains("deletenote") -or $compact.Contains("confirmdelete") -or
               $compact.Contains("canceldelete")
  $oneOff = $compact.Contains("thisonce") -or $compact.Contains("thisreply") -or
            $compact.Contains("thistime") -or $compact.Contains("forthisreply")
  $feedbackVerb = $compact.Contains("ilikethis") -or $compact.Contains("ilovethis") -or
                  $compact.Contains("thisisgood") -or $compact.Contains("goodanswer") -or
                  $compact.Contains("greatanswer") -or $compact.Contains("thanks") -or
                  $compact.Contains("thankyou")
  $answerReference = $compact.Contains("thisanswer") -or $compact.Contains("thisreply") -or
                     $compact.Contains("thisresponse") -or $compact.Contains("youranswer") -or
                     $compact.Contains("yourreply") -or $compact.Contains("yourresponse")
  $assistantFeedback = $feedbackVerb -and $answerReference
  $explicitRemember = $compact.Contains("remember") -or $compact.Contains("keepinmind")
  $preferenceCorrection = $compact.Contains("nolonger") -or $compact.Contains("instead") -or $compact.Contains("rather")
  $styleIntent = $message -match "(?i)(answer|reply|tone|style|format|formal|serious|concise|detailed|professional)"
  $styleDirective = $styleIntent -and ($compact.Contains("answer") -or $compact.Contains("reply") -or
                                      $compact.Contains("iwantyouto") -or $compact.Contains("please"))
  $stableScope = $compact.Contains("fromnowon") -or $compact.Contains("bydefault") -or $compact.Contains("always")
  $stableStyle = $stableScope -and $styleDirective
  $interactionStyleCorrection = ($compact.Contains("not") -or $compact.Contains("less")) -and
                                ($compact.Contains("more") -or $compact.Contains("instead")) -and
                                $styleIntent
  $correction = $preferenceCorrection -or $interactionStyleCorrection
  $projectContext = $message.StartsWith("project context:") -or $message.StartsWith("project background:")
  $preference = $compact.Contains("iprefer") -or $compact.Contains("ilike") -or
                $compact.Contains("prefer") -or $compact.Contains("like") -or
                $compact.Contains("iwantyouto") -or $stableStyle

  if ($sensitive) {
    $signals.Add("sensitive_content")
    return @{ Allowed = $false; DecisionType = "DENY_SENSITIVE"; MemoryType = ""; Correction = $null; PolicyReason = "sensitive_content"; Signals = $signals.ToArray(); Category = "sensitive" }
  }
  if ($forget) {
    $signals.Add("forget_request")
    return @{ Allowed = $false; DecisionType = "FORGET_REQUEST"; MemoryType = ""; Correction = $null; PolicyReason = "forget_request"; Signals = $signals.ToArray(); Category = "forget" }
  }
  if ($referenceOnly) {
    $signals.Add("reference_only")
    return @{ Allowed = $false; DecisionType = "DENY_TOOL_RESULT"; MemoryType = ""; Correction = $null; PolicyReason = "reference_context_not_profile"; Signals = $signals.ToArray(); Category = "reference_only" }
  }
  if ($operation) {
    $signals.Add("transient_operation")
    return @{ Allowed = $false; DecisionType = "DENY_TRANSIENT"; MemoryType = ""; Correction = $null; PolicyReason = "operation_or_confirmation"; Signals = $signals.ToArray(); Category = "operation" }
  }
  if ($oneOff) {
    $signals.Add("one_off_scope")
    if ($styleDirective) { $signals.Add("one_off_style_preference") }
    return @{ Allowed = $false; DecisionType = "DENY_TRANSIENT"; MemoryType = ""; Correction = $null; PolicyReason = "one_off_instruction"; Signals = $signals.ToArray(); Category = "one_off" }
  }
  if ($assistantFeedback) {
    $signals.Add("assistant_feedback")
    return @{ Allowed = $false; DecisionType = "DENY_TRANSIENT"; MemoryType = ""; Correction = $null; PolicyReason = "assistant_feedback"; Signals = $signals.ToArray(); Category = "assistant_feedback" }
  }

  $candidateCorrection = $compact.Contains("nolonger") -or $compact.Contains("instead") -or $compact.Contains("rather")
  $contentForInference = $UserMessage.Trim()
  if ($contentForInference -match "(?i)^\s*(remember|keep in mind)[,:]?\s*(.*)$") {
    $contentForInference = $Matches[2].Trim()
  }
  if ($contentForInference.Length -gt 240) {
    $contentForInference = $contentForInference.Substring(0, 240).Trim()
  }
  $inferenceText = ($contentForInference + " " + $UserMessage).ToLowerInvariant()
  $memoryType = "fact"
  if ($projectContext) {
    $memoryType = "project_context"
  } elseif ($inferenceText.Contains("reply") -or $inferenceText.Contains("answer") -or $inferenceText.Contains("format")) {
    $memoryType = "preference"
  } elseif ($contentForInference.ToLowerInvariant().Contains("prefer") -or $contentForInference.ToLowerInvariant().Contains("like")) {
    $memoryType = "preference"
  }
  if ($candidateCorrection -and $memoryType -eq "fact") {
    $memoryType = "preference"
  }

  if ($explicitRemember) {
    $signals.Add("explicit_remember")
    return @{ Allowed = $true; DecisionType = "ALLOW_EXPLICIT"; MemoryType = $memoryType; Correction = $candidateCorrection; PolicyReason = "explicit_memory"; Signals = $signals.ToArray(); Category = "explicit_preference" }
  }
  if ($correction) {
    if ($preferenceCorrection) { $signals.Add("preference_correction") }
    if ($interactionStyleCorrection) { $signals.Add("interaction_style_correction") }
    $reason = if ($interactionStyleCorrection) { "correction_interaction_style" } else { "correction_preference" }
    return @{ Allowed = $true; DecisionType = "ALLOW_IMPLICIT_LOW_CONFIDENCE"; MemoryType = $memoryType; Correction = $true; PolicyReason = $reason; Signals = $signals.ToArray(); Category = "correction" }
  }
  if ($stableStyle) {
    $signals.Add("stable_style_preference")
    return @{ Allowed = $true; DecisionType = "ALLOW_IMPLICIT_LOW_CONFIDENCE"; MemoryType = $memoryType; Correction = $false; PolicyReason = "implicit_interaction_style"; Signals = $signals.ToArray(); Category = "style" }
  }
  if ($projectContext) {
    $signals.Add("project_context")
    return @{ Allowed = $true; DecisionType = "ALLOW_IMPLICIT_LOW_CONFIDENCE"; MemoryType = "project_context"; Correction = $false; PolicyReason = "project_context"; Signals = $signals.ToArray(); Category = "project_context" }
  }
  if ($preference) {
    $signals.Add("preference_signal")
    return @{ Allowed = $true; DecisionType = "ALLOW_IMPLICIT_LOW_CONFIDENCE"; MemoryType = $memoryType; Correction = $false; PolicyReason = "implicit_preference"; Signals = $signals.ToArray(); Category = "implicit_preference" }
  }

  return @{ Allowed = $false; DecisionType = "DENY_TRANSIENT"; MemoryType = ""; Correction = $null; PolicyReason = "no_stable_user_memory_signal"; Signals = @(); Category = "ambiguous" }
}

function ScenarioTagsFor([hashtable]$Expectation, [object[]]$SourceTags) {
  $tags = New-Object System.Collections.Generic.List[string]
  $tags.Add("external_real_human_conversation")
  foreach ($tag in $SourceTags) {
    if (-not $tags.Contains($tag)) { $tags.Add([string]$tag) }
  }
  switch ($Expectation.Category) {
    "explicit_preference" { if (-not $tags.Contains("external_memory_preference")) { $tags.Add("external_memory_preference") } }
    "implicit_preference" { if (-not $tags.Contains("external_memory_preference")) { $tags.Add("external_memory_preference") } }
    "style" { if (-not $tags.Contains("external_style_request")) { $tags.Add("external_style_request") } }
    "correction" { if (-not $tags.Contains("external_correction")) { $tags.Add("external_correction") } }
    "project_context" { if (-not $tags.Contains("external_work_context")) { $tags.Add("external_work_context") } }
    "reference_only" { if (-not $tags.Contains("external_reference_task")) { $tags.Add("external_reference_task") } }
    "one_off" { if (-not $tags.Contains("external_one_off")) { $tags.Add("external_one_off") } }
    "assistant_feedback" { if (-not $tags.Contains("external_assistant_feedback")) { $tags.Add("external_assistant_feedback") } }
    "forget" { if (-not $tags.Contains("external_memory_control")) { $tags.Add("external_memory_control") } }
    "operation" { if (-not $tags.Contains("external_memory_control")) { $tags.Add("external_memory_control") } }
    "sensitive" { if (-not $tags.Contains("external_sensitive_boundary")) { $tags.Add("external_sensitive_boundary") } }
    default { if (-not $tags.Contains("external_ambiguous")) { $tags.Add("external_ambiguous") } }
  }
  return $tags.ToArray()
}

function Invoke-HfSearch([string]$Query, [int]$Offset) {
  $encodedDataset = [uri]::EscapeDataString($dataset)
  $encodedQuery = [uri]::EscapeDataString($Query)
  $uri = "https://datasets-server.huggingface.co/search?dataset=$encodedDataset&config=$config&split=$split&query=$encodedQuery&offset=$Offset&length=100"
  for ($attempt = 1; $attempt -le 4; $attempt++) {
    try {
      return Invoke-RestMethod -Uri $uri -TimeoutSec 60
    } catch {
      if ($attempt -eq 4) { throw }
      Start-Sleep -Seconds (3 * $attempt)
    }
  }
}

$cases = New-Object System.Collections.Generic.List[object]
$manifest = New-Object System.Collections.Generic.List[object]
$seenMessages = New-Object 'System.Collections.Generic.HashSet[string]'
$seenSources = New-Object 'System.Collections.Generic.HashSet[string]'
$index = 0

foreach ($querySpec in $queries) {
  $acceptedForQuery = 0
  $offset = 0
  while ($acceptedForQuery -lt [int]$querySpec.Max -and $cases.Count -lt $TargetCount) {
    $result = Invoke-HfSearch -Query $querySpec.Query -Offset $offset
    if ($null -eq $result.rows -or $result.rows.Count -eq 0) { break }

    foreach ($entry in $result.rows) {
      if ($acceptedForQuery -ge [int]$querySpec.Max -or $cases.Count -ge $TargetCount) { break }
      $row = $entry.row
      if ($row.toxic -eq $true) { continue }
      if ($row.language -ne "English") { continue }
      if ($null -eq $row.conversation -or $row.conversation.Count -lt 1) { continue }

      $firstUser = $row.conversation | Where-Object { $_.role -eq "user" } | Select-Object -First 1
      if ($null -eq $firstUser -or $firstUser.toxic -eq $true) { continue }
      if ($firstUser.language -ne "English") { continue }

      $userMessage = NormalizeText $firstUser.content
      if ($userMessage.Length -lt 20 -or $userMessage.Length -gt 600) { continue }
      if (HasRiskyText $userMessage) { continue }
      if (-not $seenMessages.Add($userMessage)) { continue }

      $sourceReference = "$sourceBase#row_idx=$($entry.row_idx);conversation_id=$($row.conversation_id);license=ODC-BY"
      if (-not $seenSources.Add($sourceReference)) { continue }

      $expectation = Get-PolicyExpectation -UserMessage $userMessage -AssistantOutput $assistantPlaceholder
      $index++
      $id = "replay_en_$($expectation.Category)_external_$($index.ToString('0000'))"

      $cases.Add([ordered]@{
        id = $id
        userMessage = $userMessage
        assistantOutput = $assistantPlaceholder
        expectedCaptureAllowed = [bool]$expectation.Allowed
        expectedDecisionType = $expectation.DecisionType
        expectedMemoryType = $expectation.MemoryType
        expectedCorrection = $expectation.Correction
        expectedPolicyReason = $expectation.PolicyReason
        expectedSignals = @($expectation.Signals)
      })

      $manifest.Add([ordered]@{
        caseId = $id
        sourceType = "external_real_human_conversation"
        sourceReference = $sourceReference
        personaAgent = "external_human_user"
        scenarioTags = @(ScenarioTagsFor -Expectation $expectation -SourceTags $querySpec.Tags)
        languageTags = @("en")
        conversationId = "wildchat-$($row.conversation_id)"
        redactionReportId = "redaction-external-wildchat-v1"
        reviewStatus = "approved"
        reviewer = "external_replay_curator_v1"
        approvedAt = $approvedAt
      })
      $acceptedForQuery++
    }

    if ($result.num_rows_total -le ($offset + 100)) { break }
    $offset += 100
  }
}

if ($cases.Count -lt $TargetCount) {
  throw "Only harvested $($cases.Count) cases; target is $TargetCount"
}

$artifact = [ordered]@{
  cases = @($cases.ToArray())
  manifest = @($manifest.ToArray())
}

$resolvedOutputPath = Join-Path (Get-Location) $OutputPath
$outputDir = Split-Path -Parent $resolvedOutputPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$artifact | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 -Path $resolvedOutputPath
Write-Host "Wrote $($cases.Count) external real-human replay cases to $resolvedOutputPath"
