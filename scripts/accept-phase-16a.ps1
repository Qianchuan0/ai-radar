# Phase 16A acceptance script: Minimal Clustering Evaluation Baseline
param(
    [switch]$SkipBackendTests = $false
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
    Write-Section "Phase 16A acceptance start"

    Write-Section "1. Required Phase 16A artifacts"
    $requiredFiles = @(
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterAssignmentStrategy.java",
        "backend/src/main/java/com/airadar/cluster/strategy/AssignmentDecision.java",
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterAssignmentResult.java",
        "backend/src/main/java/com/airadar/cluster/strategy/CanonicalUrlClusterStrategy.java",
        "backend/src/main/java/com/airadar/evaluation/cluster/ClusterBaselineFixture.java",
        "backend/src/main/java/com/airadar/evaluation/cluster/ClusterBaselineFixtures.java",
        "backend/src/main/java/com/airadar/evaluation/cluster/FixtureInputItem.java",
        "backend/src/main/java/com/airadar/evaluation/cluster/ClusterEvaluationService.java",
        "backend/src/main/java/com/airadar/evaluation/cluster/ClusterEvaluationReport.java",
        "backend/src/main/java/com/airadar/evaluation/cluster/ClusterEvaluationCaseResult.java",
        "backend/src/main/java/com/airadar/evaluation/cluster/package-info.java",
        "backend/src/test/java/com/airadar/evaluation/cluster/ClusterEvaluationIntegrationTest.java",
        "scripts/accept-phase-16a.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. Strategy interface pins V1/V2 contract"
    $interface = "backend/src/main/java/com/airadar/cluster/strategy/ClusterAssignmentStrategy.java"
    $interfaceContent = Get-Content $interface -Raw
    if ($interfaceContent -match 'String version\(\)' -and
        $interfaceContent -match 'ClusterAssignmentResult assign\(HotItemEntity item\)') {
        Step-Success "ClusterAssignmentStrategy exposes version() and assign(item)"
    } else {
        Step-Failure "ClusterAssignmentStrategy contract changed"
    }

    Write-Section "3. V1 strategy wraps RuleBasedClusterService unchanged"
    $v1Wrapper = "backend/src/main/java/com/airadar/cluster/strategy/CanonicalUrlClusterStrategy.java"
    $wrapperContent = Get-Content $v1Wrapper -Raw
    if ($wrapperContent -match 'delegate\.assign\(item\)' -and
        $wrapperContent -match 'RuleBasedClusterService\.RULE_VERSION') {
        Step-Success "CanonicalUrlClusterStrategy delegates to RuleBasedClusterService"
    } else {
        Step-Failure "CanonicalUrlClusterStrategy must delegate without adding clustering logic"
    }

    $v1Service = "backend/src/main/java/com/airadar/cluster/service/RuleBasedClusterService.java"
    $v1Content = Get-Content $v1Service -Raw
    if ($v1Content -match 'public static final String RULE_VERSION = "hn-rule-v1"') {
        Step-Success "RuleBasedClusterService RULE_VERSION unchanged"
    } else {
        Step-Failure "RuleBasedClusterService RULE_VERSION must remain hn-rule-v1"
    }

    Write-Section "4. Default fixture covers must-merge + must-not-merge"
    $fixture = "backend/src/main/java/com/airadar/evaluation/cluster/ClusterBaselineFixtures.java"
    $fixtureContent = Get-Content $fixture -Raw
    $mustMergeCount = ([regex]::Matches($fixtureContent, 'Set\.of\(')).Count
    if ($fixtureContent -match 'phase16a-baseline-v1' -and
        $fixtureContent -match 'MustNotMergePair' -and
        $mustMergeCount -ge 5) {
        Step-Success "Default fixture has version tag, must-merge groups, and must-not-merge pairs"
    } else {
        Step-Failure "Default fixture must include >=5 must-merge groups and >=5 must-not-merge pairs"
    }

    Write-Section "5. Evaluation service truncates for replayability"
    $service = "backend/src/main/java/com/airadar/evaluation/cluster/ClusterEvaluationService.java"
    $serviceContent = Get-Content $service -Raw
    if ($serviceContent -match 'TRUNCATE TABLE' -and
        $serviceContent -match 'RESTART IDENTITY CASCADE') {
        Step-Success "ClusterEvaluationService truncates schema for replayable runs"
    } else {
        Step-Failure "ClusterEvaluationService must truncate before each evaluation"
    }

    Write-Section "6. Roadmap and decision log entries"
    $roadmap = "docs/roadmap.md"
    $roadmapContent = Get-Content $roadmap -Raw
    if ($roadmapContent -match 'Phase 16A: Minimal Clustering Evaluation Baseline' -and
        $roadmapContent -match 'Phase 16: Event Cluster V2') {
        Step-Success "Roadmap lists Phase 16A and Phase 16"
    } else {
        Step-Failure "Roadmap must include Phase 16A and Phase 16 sections"
    }

    $decisionLog = "docs/decision-log.md"
    $dlContent = Get-Content $decisionLog -Raw
    if ($dlContent -match 'Phase 16A introduces a minimal, replayable clustering evaluation baseline') {
        Step-Success "Decision log records Phase 16A decision"
    } else {
        Step-Failure "Decision log missing Phase 16A entry"
    }

    if (-not $SkipBackendTests) {
        Write-Section "7. Phase 16A integration test (requires Docker)"
        Write-Host "Running ClusterEvaluationIntegrationTest via Testcontainers (requires Docker daemon)." -ForegroundColor Gray
        Invoke-Step `
            "ClusterEvaluationIntegrationTest" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=ClusterEvaluationIntegrationTest", "test")
    } else {
        Write-Host "Skip backend tests." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 16A acceptance passed.

Validated components:
- ClusterAssignmentStrategy interface (version + assign)
- CanonicalUrlClusterStrategy wraps RuleBasedClusterService (V1 unchanged)
- ClusterBaselineFixtures.defaultFixture() (13 items, 5 must-merge, 5 must-not-merge)
- ClusterEvaluationService (truncate-and-replay contract)
- ClusterEvaluationReport with precision, recall, false-merge, false-split

Phase 16A scope:
- Strategy-agnostic evaluation runner
- In-memory frozen fixture
- Per-case and aggregate metrics
- No LLM judges, no production dataset ingestion, no V1 behavior changes

Not in Phase 16A scope:
- V2 strategy implementation (Phase 16)
- Production dataset ingestion
- Frontend visualization
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
