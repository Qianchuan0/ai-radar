# Phase 14 acceptance script: signal snapshots and 24h growth trends
param(
    [switch]$SkipDockerChecks = $false
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
    Write-Section "Phase 14 acceptance start"

    Write-Section "1. Required signal snapshot files"
    $requiredFiles = @(
        "backend/src/main/resources/db/migration/V7__add_signal_snapshots.sql",
        "backend/src/main/java/com/airadar/signal/entity/SignalSnapshotEntity.java",
        "backend/src/main/java/com/airadar/signal/mapper/SignalSnapshotMapper.java",
        "backend/src/main/java/com/airadar/signal/service/SignalSnapshotService.java",
        "backend/src/main/java/com/airadar/signal/service/GrowthCalculationService.java",
        "backend/src/main/java/com/airadar/signal/model/GrowthMetrics.java",
        "backend/src/main/java/com/airadar/signal/model/GrowthConfidence.java",
        "backend/src/main/java/com/airadar/signal/controller/HotItemSignalController.java",
        "backend/src/test/java/com/airadar/signal/service/GrowthCalculationServiceTest.java",
        "backend/src/test/java/com/airadar/signal/controller/HotItemSignalControllerTest.java",
        "scripts/accept-phase-14.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. Backend compile"
    Invoke-Step `
        "Backend compile" `
        (Join-Path $ProjectRoot "backend") `
        ".\mvnw.cmd" `
        @("-DskipTests", "compile")

    Write-Section "3. Focused Phase 14 tests"
    Invoke-Step `
        "Phase 14 unit and slice tests" `
        (Join-Path $ProjectRoot "backend") `
        ".\mvnw.cmd" `
        @("-Dtest=com.airadar.signal.adapter.SourceSignalAdapterTest,com.airadar.signal.adapter.SourceSignalAdapterRegistryTest,com.airadar.signal.model.NormalizedSignalTest,com.airadar.signal.service.GrowthCalculationServiceTest,com.airadar.signal.controller.HotItemSignalControllerTest", "test")

    if (-not $SkipDockerChecks) {
        Write-Section "4. Docker availability"
        docker version | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Step-Failure "Docker is required for Testcontainers-backed verification."
        }
        Step-Success "Docker is available"
    } else {
        Write-Host "Skip Docker availability check." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 14 acceptance checks passed.

Validated closed loop:
- signal snapshot migration and backend files exist
- pipeline compiles with snapshot persistence inserted after hot item upsert
- recent signal snapshots API and 24h trend API are covered by focused tests
- growth calculation enforces 24h only, uses confidence levels, and preserves V1 scoring separation
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
