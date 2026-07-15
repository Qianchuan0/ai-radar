# Phase 13B acceptance script: source roles and normalized signal adapters
param(
    [switch]$SkipV1Replay = $false
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Write-Section {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Step-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Step-Failure {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Invoke-Step {
    param(
        [string]$Name,
        [string]$Workdir,
        [string]$Command,
        [string[]]$CommandArgs = @()
    )
    Write-Host "`n[TEST] $Name" -ForegroundColor Yellow
    Write-Host "    cwd: $Workdir"
    Write-Host "    cmd: $Command $($CommandArgs -join ' ')"
    Push-Location $Workdir
    try {
        & $Command @CommandArgs
        if ($LASTEXITCODE -ne 0) {
            Step-Failure "$Name failed with exit code $LASTEXITCODE"
        }
        Step-Success "$Name passed"
    } finally {
        Pop-Location
    }
}

Push-Location $ProjectRoot

try {
    Write-Section "Phase 13B acceptance start"

    Write-Section "1. Required signal files"
    $requiredFiles = @(
        "backend/src/main/java/com/airadar/signal/model/SourceRole.java",
        "backend/src/main/java/com/airadar/signal/model/NormalizedSignal.java",
        "backend/src/main/java/com/airadar/signal/adapter/SourceSignalAdapter.java",
        "backend/src/main/java/com/airadar/signal/adapter/SourceSignalAdapterRegistry.java",
        "backend/src/main/java/com/airadar/signal/adapter/HackerNewsSignalAdapter.java",
        "backend/src/main/java/com/airadar/signal/adapter/GitHubSignalAdapter.java",
        "backend/src/main/java/com/airadar/signal/adapter/HuggingFaceSignalAdapter.java",
        "backend/src/main/java/com/airadar/signal/adapter/SearchSignalAdapter.java",
        "backend/src/test/java/com/airadar/signal/adapter/SourceSignalAdapterTest.java",
        "backend/src/test/java/com/airadar/signal/adapter/SourceSignalAdapterRegistryTest.java",
        "backend/src/test/java/com/airadar/signal/model/NormalizedSignalTest.java",
        "docs/signal-layer-guide.md"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. Signal layer backend tests"
    Invoke-Step `
        "Signal adapter and registry tests" `
        (Join-Path $ProjectRoot "backend") `
        ".\mvnw.cmd" `
        @("-Dtest=com.airadar.signal.adapter.SourceSignalAdapterTest,com.airadar.signal.adapter.SourceSignalAdapterRegistryTest,com.airadar.signal.model.NormalizedSignalTest", "test")

    if (-not $SkipV1Replay) {
        Write-Section "3. V1 scoring compatibility"
        Invoke-Step `
            "V1 baseline replay test" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=com.airadar.baseline.V1BaselineReplayIntegrationTest", "test")
    } else {
        Write-Host "Skip V1 replay test." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 13B acceptance passed.

Validated closed loop:
- SourceRole and NormalizedSignal compile and are covered by unit tests.
- First signal adapters generate NormalizedSignal for HACKER_NEWS, GITHUB, HUGGING_FACE, BING_SEARCH, and DUCKDUCKGO_SEARCH.
- Search sources preserve rank/relevance but contribute zero social heat.
- Registry rejects duplicate adapters and missing adapters explicitly.
- V1 scoring compatibility is replayed unless skipped explicitly.
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
