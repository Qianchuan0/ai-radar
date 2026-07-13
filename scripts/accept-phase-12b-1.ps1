# Phase 12B-1 Acceptance Script
# Tests the three new JSON/API sources: Weibo Hot Search, Hacker News Search, and Twitter

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Phase 12B-1 Acceptance Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test backend
Write-Host "Testing Backend..." -ForegroundColor Yellow
Write-Host ""

$backendDir = "D:\AiProgram\ai-radar\backend"
Push-Location $backendDir

try {
    # Run unit tests
    Write-Host "Running unit tests..." -ForegroundColor Cyan
    .\mvnw.cmd "-Dtest=WeiboHotSearchClientTest,HackerNewsSearchClientTest,TwitterClientTest" test
    if ($LASTEXITCODE -ne 0) {
        throw "Unit tests failed with exit code $LASTEXITCODE"
    }
    Write-Host "✓ Unit tests passed" -ForegroundColor Green
    Write-Host ""

    # Run integration tests
    Write-Host "Running integration tests..." -ForegroundColor Cyan
    .\mvnw.cmd "-Dtest=WeiboHotSearchRawDataFlowIntegrationTest,HackerNewsSearchRawDataFlowIntegrationTest,TwitterRawDataFlowIntegrationTest" test
    if ($LASTEXITCODE -ne 0) {
        throw "Integration tests failed with exit code $LASTEXITCODE"
    }
    Write-Host "✓ Integration tests passed" -ForegroundColor Green
    Write-Host ""
}
finally {
    Pop-Location
}

# Test frontend
Write-Host "Testing Frontend..." -ForegroundColor Yellow
Write-Host ""

$frontendDir = "D:\AiProgram\ai-radar\frontend"
Push-Location $frontendDir

try {
    # Run frontend tests
    Write-Host "Running frontend tests..." -ForegroundColor Cyan
    npm test -- --run
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend tests failed with exit code $LASTEXITCODE"
    }
    Write-Host "✓ Frontend tests passed" -ForegroundColor Green
    Write-Host ""

    # Build frontend
    Write-Host "Building frontend..." -ForegroundColor Cyan
    npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed with exit code $LASTEXITCODE"
    }
    Write-Host "✓ Frontend build successful" -ForegroundColor Green
    Write-Host ""
}
finally {
    Pop-Location
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All Phase 12B-1 acceptance tests passed!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Phase 12B-1 includes:" -ForegroundColor White
Write-Host "  • WEIBO_HOT_SEARCH - 微博热搜" -ForegroundColor White
Write-Host "  • HACKER_NEWS_SEARCH - Hacker News 搜索" -ForegroundColor White
Write-Host "  • TWITTER - Twitter/X" -ForegroundColor White
Write-Host ""
Write-Host "Note: Twitter API tests use mocks and do not require a real API key." -ForegroundColor Gray
