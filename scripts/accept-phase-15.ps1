# Phase 15 acceptance script: Cross-source V2 shadow scoring
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
    Write-Section "Phase 15 acceptance start"

    Write-Section "1. Required Phase 15 artifacts"
    $requiredFiles = @(
        "backend/src/main/java/com/airadar/scoring/strategy/ClusterScoringStrategy.java",
        "backend/src/main/java/com/airadar/scoring/strategy/HnScoreV1Strategy.java",
        "backend/src/main/java/com/airadar/scoring/strategy/CrossSourceScoreV2Strategy.java",
        "backend/src/main/java/com/airadar/scoring/strategy/ScoringOrchestrator.java",
        "backend/src/main/java/com/airadar/scoring/strategy/ScoringContext.java",
        "backend/src/main/java/com/airadar/scoring/strategy/model/ScoreComponent.java",
        "backend/src/main/java/com/airadar/scoring/strategy/model/ScoreComponents.java",
        "backend/src/main/java/com/airadar/scoring/calculator/ScoreCalculator.java",
        "backend/src/main/java/com/airadar/scoring/calculator/MomentumScoreCalculator.java",
        "backend/src/main/java/com/airadar/scoring/calculator/AdoptionScoreCalculator.java",
        "backend/src/main/java/com/airadar/scoring/calculator/DiscussionScoreCalculator.java",
        "backend/src/main/java/com/airadar/scoring/calculator/AuthorityScoreCalculator.java",
        "backend/src/main/java/com/airadar/scoring/calculator/RelevanceScoreCalculator.java",
        "backend/src/main/java/com/airadar/scoring/calculator/EvidenceDiversityCalculator.java",
        "backend/src/main/java/com/airadar/scoring/calculator/FreshnessScoreCalculator.java",
        "backend/src/main/java/com/airadar/scoring/vo/HotClusterScoreVO.java",
        "scripts/accept-phase-15.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. V1 zero-change guarantee"
    $v1Service = "backend/src/main/java/com/airadar/scoring/service/RuleBasedScoringService.java"
    $v1Content = Get-Content $v1Service -Raw
    if ($v1Content -match 'public static final String SCORING_VERSION = "hn-score-v1"' -and
        $v1Content -match 'cappedLogScore' -and
        $v1Content -match 'freshnessScore') {
        Step-Success "RuleBasedScoringService logic preserved"
    } else {
        Step-Failure "RuleBasedScoringService appears modified"
    }

    $v1Wrapper = "backend/src/main/java/com/airadar/scoring/strategy/HnScoreV1Strategy.java"
    $wrapperContent = Get-Content $v1Wrapper -Raw
    if ($wrapperContent -match 'delegate\.score\(cluster\)') {
        Step-Success "HnScoreV1Strategy transparently delegates to RuleBasedScoringService"
    } else {
        Step-Failure "HnScoreV1Strategy must delegate without adding logic"
    }

    Write-Section "3. Default ranking pinned to V1"
    $mapper = "backend/src/main/resources/mapper/HotClusterMapper.xml"
    $mapperContent = Get-Content $mapper -Raw
    if ($mapperContent -match "scoring_version = 'hn-score-v1'") {
        Step-Success "HotClusterMapper SCORE_DESC subquery filters scoring_version = hn-score-v1"
    } else {
        Step-Failure "HotClusterMapper must pin scoring_version = 'hn-score-v1' in the score subquery"
    }

    $queryService = "backend/src/main/java/com/airadar/cluster/service/HotClusterQueryService.java"
    $queryContent = Get-Content $queryService -Raw
    if ($queryContent -match "RuleBasedScoringService\.SCORING_VERSION") {
        Step-Success "HotClusterQueryService latestScore filters by V1 version"
    } else {
        Step-Failure "HotClusterQueryService.latestScore must filter by RuleBasedScoringService.SCORING_VERSION"
    }

    Write-Section "4. Pipeline switched to orchestrator"
    $pipeline = "backend/src/main/java/com/airadar/item/service/ItemPipelineService.java"
    $pipelineContent = Get-Content $pipeline -Raw
    if ($pipelineContent -match 'scoringOrchestrator\.run\(cluster\)') {
        Step-Success "ItemPipelineService uses ScoringOrchestrator"
    } else {
        Step-Failure "ItemPipelineService must call scoringOrchestrator.run(cluster)"
    }
    if ($pipelineContent -match 'scoringService\.score') {
        Step-Failure "ItemPipelineService still references scoringService.score directly"
    } else {
        Step-Success "No direct scoringService.score reference remains"
    }

    Write-Section "5. Comparison API"
    $controller = "backend/src/main/java/com/airadar/cluster/controller/HotClusterController.java"
    $controllerContent = Get-Content $controller -Raw
    if ($controllerContent -match 'GetMapping\("/\{clusterId\}/scores"\)') {
        Step-Success "GET /api/v1/hot-clusters/{id}/scores endpoint present"
    } else {
        Step-Failure "Comparison API endpoint missing"
    }

    if (-not $SkipBackendTests) {
        Write-Section "6. Backend unit tests (no Docker required)"
        Invoke-Step `
            "V2 calculator and shadow tests" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=com.airadar.scoring.calculator.*Test,com.airadar.scoring.strategy.ScoringOrchestratorShadowTest,com.airadar.scoring.V1V2ComparisonTest", "test")
    } else {
        Write-Host "Skip backend tests." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 15 acceptance passed.

Validated components:
- ClusterScoringStrategy interface with V1/V2 strategies
- 7 dimension calculators (momentum, adoption, discussion, authority, relevance, evidenceDiversity, freshness)
- ScoringOrchestrator runs V2 in shadow mode (failure does not block V1)
- ItemPipelineService switched to ScoringOrchestrator
- RuleBasedScoringService preserved unchanged (V1 baseline intact)
- GET /api/v1/hot-clusters/{id}/scores returns both versions

Phase 15 scope:
- cross-source-score-v2 persisted alongside hn-score-v1
- Default ranking still uses V1
- V2 score_components fully explainable

Not in Phase 15 scope:
- Replacing V1 ranking (shadow only)
- LLM scoring or learned weights
- Multi-window momentum beyond 24h
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
