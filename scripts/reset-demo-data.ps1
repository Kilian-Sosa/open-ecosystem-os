$ErrorActionPreference = "Stop"

$apiBaseUrl = $env:API_BASE_URL
if (-not $apiBaseUrl) {
  $apiBaseUrl = "http://localhost:8080"
}

$workspaceId = $env:WORKSPACE_ID
if (-not $workspaceId) {
  $workspaceId = "wrk_dev_placeholder"
}

$actorId = $env:ACTOR_ID
if (-not $actorId) {
  $actorId = "usr_dev_placeholder"
}

Write-Host "Resetting fake/test invoice automation demo data against $apiBaseUrl"
Invoke-RestMethod `
  -Method Post `
  -Uri "$apiBaseUrl/api/demo/invoice-automation/reset" `
  -Headers @{
    "X-Workspace-Id" = $workspaceId
    "X-Actor-Id" = $actorId
  } | ConvertTo-Json -Depth 8
