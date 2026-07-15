# Phase 13A acceptance script: V1 baseline freeze and prior phase closure
param(
    [switch]$SkipPreviousPhaseScripts = $false,
    [switch]$SkipFrontendTests = $false,
    [switch]$SkipFrontendBuild = $false
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
    Write-Section "Phase 13A acceptance start"

    Write-Section "1. Required baseline artifacts"
    $requiredFiles = @(
        "docs/v1-baseline-behavior.md",
        "docs/phase11a-acceptance.md",
        "docs/phase11b-acceptance.md",
        "docs/phase12a-acceptance.md",
        "docs/phase12b-1-acceptance.md",
        "docs/phase12b-2-acceptance.md",
        "backend/src/test/java/com/airadar/baseline/V1BaselineReplayIntegrationTest.java"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    if (-not $SkipPreviousPhaseScripts) {
        Write-Section "2. Prior phase acceptance scripts"
        $scripts = @(
            "accept-phase-11a.ps1",
            "accept-phase-11b.ps1",
            "accept-phase-12a.ps1",
            "accept-phase-12b-1.ps1",
            "accept-phase-12b-2.ps1"
        )
        foreach ($script in $scripts) {
            Invoke-Step $script $ProjectRoot ".\scripts\$script"
        }
    } else {
        Write-Host "Skip prior phase acceptance scripts." -ForegroundColor Gray
    }

    Write-Section "3. V1 replay baseline"
    Invoke-Step `
        "Backend V1 baseline replay test" `
        (Join-Path $ProjectRoot "backend") `
        ".\mvnw.cmd" `
        @("-Dtest=com.airadar.baseline.V1BaselineReplayIntegrationTest", "test")

    if (-not $SkipFrontendTests) {
        Write-Section "4. Frontend tests"
        Invoke-Step "Frontend unit tests" (Join-Path $ProjectRoot "frontend") "npm" @("test", "--", "--run")
    } else {
        Write-Host "Skip frontend tests." -ForegroundColor Gray
    }

    if (-not $SkipFrontendBuild) {
        Write-Section "5. Frontend build"
        Invoke-Step "Frontend production build" (Join-Path $ProjectRoot "frontend") "npm" @("run", "build")
    } else {
        Write-Host "Skip frontend build." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 13A acceptance passed.

Validated closed loop:
- Phase 11A/11B/12A/12B-1/12B-2 acceptance scripts remain repeatable.
- V1 clustering baseline is replayed by V1BaselineReplayIntegrationTest.
- V1 scoring baseline is replayed by V1BaselineReplayIntegrationTest.
- Frontend tests/build remain green unless skipped explicitly.
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
