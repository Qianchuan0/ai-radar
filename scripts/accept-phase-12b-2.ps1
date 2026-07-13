# Phase 12B-2 acceptance script: HTML search sources (Bing + DuckDuckGo)
param(
    [switch]$SkipBackendTests = $false,
    [switch]$SkipFrontendTests = $false,
    [switch]$SkipFrontendBuild = $false
)

$ErrorActionPreference = "Stop"
$ProjectRoot = "D:\AiProgram\ai-radar"

function Write-Section {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Test-Step {
    param([string]$Message)
    Write-Host "`n[TEST] $Message" -ForegroundColor Yellow
}

function Step-Success {
    param([string]$Message = "Passed")
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Step-Failure {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

Push-Location $ProjectRoot

try {
    Write-Section "Phase 12B-2 acceptance start"

    Write-Section "1. Required files"
    $backendFiles = @(
        "backend/src/main/java/com/airadar/crawl/client/htmlsearch/HtmlSearchHeaders.java",
        "backend/src/main/java/com/airadar/crawl/client/htmlsearch/HtmlSearchResult.java",
        "backend/src/main/java/com/airadar/crawl/client/htmlsearch/HtmlSearchUrlSanitizer.java",
        "backend/src/main/java/com/airadar/crawl/client/htmlsearch/HtmlSearchBlockDetector.java",
        "backend/src/main/java/com/airadar/crawl/client/htmlsearch/HtmlSearchParseException.java",
        "backend/src/main/java/com/airadar/crawl/client/bing/BingSearchProperties.java",
        "backend/src/main/java/com/airadar/crawl/client/bing/BingSearchRequest.java",
        "backend/src/main/java/com/airadar/crawl/client/bing/FetchedBingSearchResult.java",
        "backend/src/main/java/com/airadar/crawl/client/bing/BingSearchClient.java",
        "backend/src/main/java/com/airadar/crawl/collector/bing/BingSearchCollector.java",
        "backend/src/main/java/com/airadar/item/normalizer/BingSearchHotItemNormalizer.java",
        "backend/src/main/java/com/airadar/crawl/client/duckduckgo/DuckDuckGoSearchProperties.java",
        "backend/src/main/java/com/airadar/crawl/client/duckduckgo/DuckDuckGoSearchRequest.java",
        "backend/src/main/java/com/airadar/crawl/client/duckduckgo/FetchedDuckDuckGoSearchResult.java",
        "backend/src/main/java/com/airadar/crawl/client/duckduckgo/DuckDuckGoSearchClient.java",
        "backend/src/main/java/com/airadar/crawl/collector/duckduckgo/DuckDuckGoSearchCollector.java",
        "backend/src/main/java/com/airadar/item/normalizer/DuckDuckGoSearchHotItemNormalizer.java"
    )

    foreach ($file in $backendFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. SourceType enum"
    $sourceTypeContent = Get-Content "backend/src/main/java/com/airadar/source/model/SourceType.java" -Raw
    if ($sourceTypeContent -match "BING_SEARCH") {
        Step-Success "SourceType contains BING_SEARCH"
    } else {
        Step-Failure "SourceType missing BING_SEARCH"
    }
    if ($sourceTypeContent -match "DUCKDUCKGO_SEARCH") {
        Step-Success "SourceType contains DUCKDUCKGO_SEARCH"
    } else {
        Step-Failure "SourceType missing DUCKDUCKGO_SEARCH"
    }

    Write-Section "3. application.yml config"
    $applicationContent = Get-Content "backend/src/main/resources/application.yml" -Raw
    if ($applicationContent -match "bing-search") {
        Step-Success "application.yml contains bing-search config"
    } else {
        Step-Failure "application.yml missing bing-search config"
    }
    if ($applicationContent -match "duckduckgo-search") {
        Step-Success "application.yml contains duckduckgo-search config"
    } else {
        Step-Failure "application.yml missing duckduckgo-search config"
    }

    Write-Section "4. jsoup dependency"
    $pomContent = Get-Content "backend/pom.xml" -Raw
    if ($pomContent -match "jsoup") {
        Step-Success "pom.xml contains jsoup dependency"
    } else {
        Step-Failure "pom.xml missing jsoup dependency"
    }

    if (-not $SkipBackendTests) {
        Write-Section "5. Backend tests"
        Push-Location backend
        try {
            Test-Step "Compile backend"
            .\mvnw.cmd clean compile -q
            if ($LASTEXITCODE -eq 0) {
                Step-Success "Backend compile passed"
            } else {
                Step-Failure "Backend compile failed"
            }

            Test-Step "Run HTML search backend tests"
            .\mvnw.cmd "-Dtest=*BingSearch*Test,*DuckDuckGoSearch*Test,*HtmlSearch*Test" test -q
            if ($LASTEXITCODE -eq 0) {
                Step-Success "Backend tests passed"
            } else {
                Step-Failure "Backend tests failed"
            }
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "Skip backend tests." -ForegroundColor Gray
    }

    if (-not $SkipFrontendTests) {
        Write-Section "6. Frontend tests"
        Push-Location frontend
        try {
            Test-Step "Run frontend unit tests"
            npm test -- --run
            if ($LASTEXITCODE -eq 0) {
                Step-Success "Frontend tests passed"
            } else {
                Step-Failure "Frontend tests failed"
            }
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "Skip frontend tests." -ForegroundColor Gray
    }

    if (-not $SkipFrontendBuild) {
        Write-Section "7. Frontend build"
        Push-Location frontend
        try {
            Test-Step "Build frontend"
            npm run build
            if ($LASTEXITCODE -eq 0) {
                Step-Success "Frontend build passed"
            } else {
                Step-Failure "Frontend build failed"
            }
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "Skip frontend build." -ForegroundColor Gray
    }

    Write-Section "8. Frontend source type updates"
    $frontendFiles = @(
        "frontend/src/shared/api/contracts.ts",
        "frontend/src/shared/utils/query.ts",
        "frontend/src/shared/utils/query.test.ts"
    )

    foreach ($file in $frontendFiles) {
        $content = Get-Content $file -Raw
        if ($content -match "BING_SEARCH" -and $content -match "DUCKDUCKGO_SEARCH") {
            Step-Success "Frontend file updated: $file"
        } else {
            Step-Failure "Frontend file missing source types: $file"
        }
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 12B-2 HTML search sources acceptance passed.

Source types:
- BING_SEARCH
- DUCKDUCKGO_SEARCH

Notes:
- Google Search is not included in Phase 12B-2A.
- HTML sources use maxAttempts=1 by default.
- Recommended crawlIntervalMinutes is at least 180.
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
