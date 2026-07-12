<#
.SYNOPSIS
  Optional live verification for Phase 10 OpenAI structured analysis.
  Only run this script when you have a valid OPENAI_API_KEY and are OK
  spending real OpenAI tokens. It is NOT part of the default acceptance path.

.PREREQUISITES
  - PostgreSQL running (docker compose up postgres, or local install)
  - OPENAI_API_KEY environment variable set
  - Backend running with ai-radar.analysis.provider=openai (default) and
    AI_RADAR_OPENAI_API_KEY=$OPENAI_API_KEY
  - At least one cluster with evidence items in the database

.USAGE
  powershell -File .\scripts\live-verify-openai.ps1 -ClusterId <id>
  powershell -File .\scripts\live-verify-openai.ps1 -ClusterId 7 -BackendUrl http://localhost:8080
#>

param(
    [Parameter(Mandatory = $true)]
    [long]$ClusterId,
    [string]$BackendUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

if (-not $env:OPENAI_API_KEY) {
    Write-Host "OPENAI_API_KEY is not set. Live verification aborted." -ForegroundColor Yellow
    Write-Host "Set it in your shell before running this script:"
    Write-Host '  $env:OPENAI_API_KEY = "sk-..."'
    exit 1
}

Write-Host "==> Triggering analysis run for cluster $ClusterId" -ForegroundColor Cyan
$triggerResponse = Invoke-RestMethod -Method Post `
    -Uri "$BackendUrl/api/v1/hot-clusters/$ClusterId/analysis-runs" `
    -Headers @{ Accept = "application/json" } `
    -ContentType "application/json"

$triggerResponse | ConvertTo-Json -Depth 10

if ($triggerResponse.data.status -ne "SUCCEEDED") {
    Write-Host ""
    Write-Host "Analysis did not succeed. failureCode=$($triggerResponse.data.failureCode)" -ForegroundColor Yellow
    Write-Host "Inspect cluster_analysis.failure_message for upstream details."
    exit 2
}

Write-Host ""
Write-Host "==> Reading latest analysis via GET /analysis" -ForegroundColor Cyan
$latestResponse = Invoke-RestMethod -Method Get `
    -Uri "$BackendUrl/api/v1/hot-clusters/$ClusterId/analysis" `
    -Headers @{ Accept = "application/json" }

$latestResponse | ConvertTo-Json -Depth 10

Write-Host ""
Write-Host "Live OpenAI structured analysis verified." -ForegroundColor Green
Write-Host "modelProvider=$($latestResponse.data.modelProvider) modelName=$($latestResponse.data.modelName)"
Write-Host "confidence=$($latestResponse.data.result.confidence) headline=$($latestResponse.data.result.headline)"
