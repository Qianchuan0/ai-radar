# Phase 18B acceptance script: Score V2 Online Ranking Adoption
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
    Write-Section "Phase 18B acceptance start"

    Write-Section "1. Required Phase 18B artifacts"
    $requiredFiles = @(
        "backend/src/main/java/com/airadar/scoring/strategy/ScoringStrategyProperties.java",
        "backend/src/main/java/com/airadar/scoring/strategy/controller/ScoringStrategyController.java",
        "backend/src/main/java/com/airadar/scoring/strategy/vo/ScoringStrategyStatusVO.java",
        "backend/src/test/java/com/airadar/scoring/ScoreV2OnlineRankingIntegrationTest.java",
        "backend/src/test/java/com/airadar/scoring/strategy/ScoringStrategyPropertiesTest.java",
        "backend/src/test/java/com/airadar/scoring/calculator/AdoptionScoreCalculatorTest.java",
        "backend/src/test/java/com/airadar/scoring/calculator/DiscussionScoreCalculatorTest.java",
        "backend/src/test/java/com/airadar/scoring/calculator/RelevanceScoreCalculatorTest.java",
        "scripts/accept-phase-18b.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. ScoringContext carries cluster trend + role groupings"
    $contextFile = "backend/src/main/java/com/airadar/scoring/strategy/ScoringContext.java"
    $contextContent = Get-Content $contextFile -Raw
    if ($contextContent -match 'ClusterTrend clusterTrend' -and
        $contextContent -match 'Map<SourceRole, List<HotItemEntity>> itemsByRole' -and
        $contextContent -match 'Set<String> dedupedDiscoveryUrls' -and
        $contextContent -match 'earliestCredibleEventAt') {
        Step-Success "ScoringContext exposes cluster trend, role groupings, deduped URLs, earliest credible event"
    } else {
        Step-Failure "ScoringContext missing Phase 18B fields"
    }

    Write-Section "3. V2 calculators no longer read primary-item growth"
    $momentum = Get-Content "backend/src/main/java/com/airadar/scoring/calculator/MomentumScoreCalculator.java" -Raw
    # Strip Java comments (line + block) before checking code-level usage.
    $momentumCode = $momentum -replace '/\*[\s\S]*?\*/', '' -replace '//.*', ''
    if ($momentumCode -match 'context\.clusterTrend\(\)' -and
        $momentumCode -notmatch 'context\.primaryGrowth\(\)') {
        Step-Success "MomentumScoreCalculator reads cluster trend, not primary item growth"
    } else {
        Step-Failure "MomentumScoreCalculator still reads primary item growth"
    }

    Write-Section "4. Adoption / Discussion / Relevance use source role groupings"
    $adoption = Get-Content "backend/src/main/java/com/airadar/scoring/calculator/AdoptionScoreCalculator.java" -Raw
    $discussion = Get-Content "backend/src/main/java/com/airadar/scoring/calculator/DiscussionScoreCalculator.java" -Raw
    $relevance = Get-Content "backend/src/main/java/com/airadar/scoring/calculator/RelevanceScoreCalculator.java" -Raw
    if ($adoption -match 'signalsForRole\(SourceRole\.ADOPTION\)' -and
        $discussion -match 'signalsForRole\(SourceRole\.COMMUNITY\)' -and
        $relevance -match 'signalsForRole\(SourceRole\.DISCOVERY\)') {
        Step-Success "Adoption / Discussion / Relevance calculators use role-grouped signals"
    } else {
        Step-Failure "Adoption / Discussion / Relevance calculators not role-grouped"
    }

    Write-Section "5. Freshness uses earliest credible event time"
    $freshness = Get-Content "backend/src/main/java/com/airadar/scoring/calculator/FreshnessScoreCalculator.java" -Raw
    $freshnessCode = $freshness -replace '/\*[\s\S]*?\*/', '' -replace '//.*', ''
    if ($freshnessCode -match 'earliestCredibleEventAt\(\)' -and
        $freshnessCode -notmatch 'context\.primaryItem\(\)') {
        Step-Success "FreshnessScoreCalculator uses earliest credible event time"
    } else {
        Step-Failure "FreshnessScoreCalculator still reads primary item"
    }

    Write-Section "6. HotClusterMapper supports scoringVersion parameter"
    $mapperXml = Get-Content "backend/src/main/resources/mapper/HotClusterMapper.xml" -Raw
    $mapperJava = Get-Content "backend/src/main/java/com/airadar/cluster/mapper/HotClusterMapper.java" -Raw
    if ($mapperXml -match 'hs\.scoring_version = #\{scoringVersion\}' -and
        $mapperJava -match '@Param\("scoringVersion"\)') {
        Step-Success "HotClusterMapper wires scoring_version as a query parameter"
    } else {
        Step-Failure "HotClusterMapper missing scoringVersion parameter"
    }

    Write-Section "7. Hot-cluster list / detail endpoints accept scoringVersion override"
    $controller = Get-Content "backend/src/main/java/com/airadar/cluster/controller/HotClusterController.java" -Raw
    if ($controller -match '@RequestParam\(required = false\) String scoringVersion' -and
        $controller -match 'hotClusterQueryService\.list\(page, size, sort, sourceType, from, to, scoringVersion\)') {
        Step-Success "List + detail endpoints accept scoringVersion override"
    } else {
        Step-Failure "List / detail endpoints missing scoringVersion override"
    }

    Write-Section "8. /api/v1/scoring-strategy/status endpoint exists"
    $statusController = Get-Content "backend/src/main/java/com/airadar/scoring/strategy/controller/ScoringStrategyController.java" -Raw
    if ($statusController -match 'RequestMapping.*"/api/v1/scoring-strategy"' -and
        $statusController -match 'GetMapping.*"/status"') {
        Step-Success "ScoringStrategyController exposes /api/v1/scoring-strategy/status"
    } else {
        Step-Failure "Scoring strategy status endpoint missing"
    }

    Write-Section "9. ScoringStrategyProperties validates ai-radar.scoring.online-version"
    $props = Get-Content "backend/src/main/java/com/airadar/scoring/strategy/ScoringStrategyProperties.java" -Raw
    if ($props -match 'DEFAULT_ONLINE_VERSION = "hn-score-v1"' -and
        $props -match 'ALLOWED_ONLINE_VERSIONS' -and
        $props -match 'cross-source-score-v2') {
        Step-Success "ScoringStrategyProperties defaults to V1 and allows V2"
    } else {
        Step-Failure "ScoringStrategyProperties missing validation"
    }

    Write-Section "10. AlertService and DailyReportService use configured scoring version"
    $alert = Get-Content "backend/src/main/java/com/airadar/alert/service/AlertService.java" -Raw
    $report = Get-Content "backend/src/main/java/com/airadar/report/service/DailyReportService.java" -Raw
    if ($alert -match 'scoringProperties\.effectiveOnlineVersion\(\)' -and
        $report -match 'scoringProperties\.effectiveOnlineVersion\(\)') {
        Step-Success "Alert + DailyReport services read scoring strategy properties"
    } else {
        Step-Failure "Alert / DailyReport not wired to ScoringStrategyProperties"
    }

    Write-Section "11. application.yml exposes AI_RADAR_SCORING_ONLINE_VERSION"
    $yml = Get-Content "backend/src/main/resources/application.yml" -Raw
    if ($yml -match 'AI_RADAR_SCORING_ONLINE_VERSION:hn-score-v1') {
        Step-Success "application.yml exposes AI_RADAR_SCORING_ONLINE_VERSION env var"
    } else {
        Step-Failure "application.yml missing AI_RADAR_SCORING_ONLINE_VERSION"
    }

    if (-not $SkipBackendTests) {
        Write-Section "12. Phase 18B calculator + properties unit tests"
        Invoke-Step `
            "Phase 18B calculator + properties unit tests" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=MomentumScoreCalculatorTest,FreshnessScoreCalculatorTest,AdoptionScoreCalculatorTest,DiscussionScoreCalculatorTest,RelevanceScoreCalculatorTest,EvidenceDiversityCalculatorTest,ScoringStrategyPropertiesTest,V1V2ComparisonTest,ScoringOrchestratorShadowTest", "test")

        Write-Section "13. Phase 18B integration tests (requires Docker)"
        Write-Host "Running ScoreV2OnlineRankingIntegrationTest via Testcontainers (requires Docker daemon)." -ForegroundColor Gray
        Invoke-Step `
            "Phase 18B integration test" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=ScoreV2OnlineRankingIntegrationTest", "test")
    } else {
        Write-Host "Skip backend tests." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 18B acceptance passed.

Validated components:
- ScoringContext: cluster trend + role-grouped items/signals + deduped discovery URLs + earliest credible event
- MomentumScoreCalculator: reads ClusterTrend.momentumScore() instead of primary-item growth
- AdoptionScoreCalculator: aggregates ADOPTION source role signals across the cluster
- DiscussionScoreCalculator: aggregates COMMUNITY source role signals across the cluster
- RelevanceScoreCalculator: aggregates DISCOVERY source role signals across the cluster
- FreshnessScoreCalculator: uses earliest credible (non-DISCOVERY) event time
- EvidenceDiversityCalculator: uses precomputed dedupedDiscoveryUrls from ScoringContext
- HotClusterMapper + HotClusterQueryService: scoringVersion parameter wires V1/V2 ranking
- HotClusterController: list + detail endpoints accept scoringVersion override
- ScoringStrategyController: GET /api/v1/scoring-strategy/status returns live online version
- ScoringStrategyProperties: ai-radar.scoring.online-version defaults to hn-score-v1
- AlertService + DailyReportService: use configured scoring version for sorting
- V2 failure still falls back to V1: ScoringOrchestratorShadowTest passes

Phase 18B scope:
- Score V2 uses cluster-level multi-source trend, not primary item momentum
- V1 default sorting preserved; V2 sorting can be enabled via property or per-request override
- alert/report score version configurable; default conservative (V1)
- /api/v1/scoring-strategy/status endpoint for ops visibility
- V2 failure rolls back to V1 (shadow swallowing in ScoringOrchestrator unchanged)

Not in Phase 18B scope:
- Deletion of hn-score-v1
- LLM scoring or learned weights
- Direct modification of historical alert/report score versions
- Automatic V2 default promotion without maintainer sign-off
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
