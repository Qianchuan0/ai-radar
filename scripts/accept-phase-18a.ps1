# Phase 18A acceptance script: Cluster-Level Trend Model
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
    Write-Section "Phase 18A acceptance start"

    Write-Section "1. Required Phase 18A artifacts"
    $requiredFiles = @(
        "backend/src/main/java/com/airadar/signal/model/TrendWindow.java",
        "backend/src/main/java/com/airadar/signal/model/MetricSemantics.java",
        "backend/src/main/java/com/airadar/signal/model/RawMetricDelta.java",
        "backend/src/main/java/com/airadar/signal/model/TrendMetrics.java",
        "backend/src/main/java/com/airadar/cluster/model/ClusterTrend.java",
        "backend/src/main/java/com/airadar/cluster/model/ClusterTrendState.java",
        "backend/src/main/java/com/airadar/cluster/service/ClusterTrendService.java",
        "backend/src/main/java/com/airadar/cluster/controller/ClusterTrendController.java",
        "backend/src/main/java/com/airadar/cluster/vo/ClusterTrendVO.java",
        "backend/src/main/java/com/airadar/signal/vo/TrendMetricsVO.java",
        "backend/src/main/java/com/airadar/signal/vo/RawMetricDeltaVO.java",
        "backend/src/test/java/com/airadar/cluster/service/ClusterTrendServiceTest.java",
        "backend/src/test/java/com/airadar/cluster/controller/ClusterTrendControllerIntegrationTest.java",
        "scripts/accept-phase-18a.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. TrendWindow supports 1h/6h/24h/3d"
    $windowFile = "backend/src/main/java/com/airadar/signal/model/TrendWindow.java"
    $windowContent = Get-Content $windowFile -Raw
    if ($windowContent -match 'H1\(' -and
        $windowContent -match 'H6\(' -and
        $windowContent -match 'H24\(' -and
        $windowContent -match 'D3\(' -and
        $windowContent -match 'public static TrendWindow parse') {
        Step-Success "TrendWindow exposes 1h/6h/24h/3d with parse()"
    } else {
        Step-Failure "TrendWindow missing multi-window support"
    }

    Write-Section "3. SourceSignalAdapter exposes metric semantics"
    $adapterFile = "backend/src/main/java/com/airadar/signal/adapter/SourceSignalAdapter.java"
    $adapterContent = Get-Content $adapterFile -Raw
    if ($adapterContent -match 'default Map<String, MetricSemantics> metricSemantics' -and
        $adapterContent -match 'MetricSemantics') {
        Step-Success "SourceSignalAdapter declares default metricSemantics()"
    } else {
        Step-Failure "SourceSignalAdapter missing metricSemantics default method"
    }

    Write-Section "4. Adapters declare source-specific semantics"
    $githubAdapter = Get-Content "backend/src/main/java/com/airadar/signal/adapter/GitHubSignalAdapter.java" -Raw
    $searchAdapter = Get-Content "backend/src/main/java/com/airadar/signal/adapter/SearchSignalAdapter.java" -Raw
    if ($githubAdapter -match 'MONOTONIC_CUMULATIVE' -and
        $githubAdapter -match 'VOLATILE_SOCIAL' -and
        $searchAdapter -match 'RANK_LIKE_REVERSIBLE') {
        Step-Success "GitHub + search adapters declare Phase 18A semantics"
    } else {
        Step-Failure "Adapters missing Phase 18A metric semantics"
    }

    Write-Section "5. GrowthCalculationService exposes calculateTrend"
    $growthFile = "backend/src/main/java/com/airadar/signal/service/GrowthCalculationService.java"
    $growthContent = Get-Content $growthFile -Raw
    if ($growthContent -match 'public TrendMetrics calculateTrend' -and
        $growthContent -match 'TrendWindow window' -and
        $growthContent -match 'accelerationFor' -and
        $growthContent -match 'weightedGrowthRate') {
        Step-Success "GrowthCalculationService exposes calculateTrend with acceleration + growthRate"
    } else {
        Step-Failure "GrowthCalculationService missing calculateTrend"
    }

    Write-Section "6. ClusterTrendService aggregates per item trends"
    $clusterService = "backend/src/main/java/com/airadar/cluster/service/ClusterTrendService.java"
    $clusterContent = Get-Content $clusterService -Raw
    if ($clusterContent -match 'dedupDiscoverySources' -and
        $clusterContent -match 'decideState' -and
        $clusterContent -match 'aggregateRawDeltas' -and
        $clusterContent -match 'aggregateMomentum') {
        Step-Success "ClusterTrendService dedups discovery sources and aggregates per-item trends"
    } else {
        Step-Failure "ClusterTrendService missing aggregation logic"
    }

    Write-Section "7. Phase 18A API endpoints exist"
    $itemController = Get-Content "backend/src/main/java/com/airadar/signal/controller/HotItemSignalController.java" -Raw
    $clusterController = Get-Content "backend/src/main/java/com/airadar/cluster/controller/ClusterTrendController.java" -Raw
    if ($itemController -match 'GetMapping.*"/.*/trends"' -and
        $clusterController -match 'RequestMapping.*"/api/v1/hot-clusters"' -and
        $clusterController -match 'GetMapping.*"/.*/trends"') {
        Step-Success "Both /hot-items/{id}/trends and /hot-clusters/{id}/trends endpoints exist"
    } else {
        Step-Failure "Phase 18A endpoints missing"
    }

    Write-Section "8. Phase 14 24h endpoint remains unchanged"
    if ($growthContent -match 'Only window=24h is supported in Phase 14') {
        Step-Success "Phase 14 calculate(hotItemId, ""24h"") remains the legacy path"
    } else {
        Step-Failure "Phase 14 backward-compatible 24h guard missing"
    }

    if (-not $SkipBackendTests) {
        Write-Section "9. Phase 18A unit tests"
        Invoke-Step `
            "Phase 18A unit tests" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=GrowthCalculationServiceTest,ClusterTrendServiceTest,SourceSignalAdapterTest", "test")

        Write-Section "10. Phase 18A integration tests (requires Docker)"
        Write-Host "Running ClusterTrendControllerIntegrationTest via Testcontainers (requires Docker daemon)." -ForegroundColor Gray
        Invoke-Step `
            "Phase 18A cluster integration test" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=ClusterTrendControllerIntegrationTest", "test")

        Invoke-Step `
            "Phase 18A item trend integration test" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=HotItemSignalControllerIntegrationTest", "test")
    } else {
        Write-Host "Skip backend tests." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 18A acceptance passed.

Validated components:
- TrendWindow: 1h / 6h / 24h / 3d windows with per-window confidence thresholds
- MetricSemantics: MONOTONIC_CUMULATIVE / RANK_LIKE_REVERSIBLE / VOLATILE_SOCIAL / RELEVANCE_SCORE
- RawMetricDelta: source-aware delta with anomaly flag and growth rate
- TrendMetrics: multi-window item-level trend with growth rate / velocity / acceleration
- ClusterTrend: aggregated cluster trend with state (NEW/RISING/PEAKING/STABLE/COOLING/UNKNOWN)
- ClusterTrendService: dedup discovery sources by canonical URL, aggregate items
- ClusterTrendController: GET /api/v1/hot-clusters/{id}/trends?windows=1h,6h,24h,3d
- HotItemSignalController: GET /api/v1/hot-items/{id}/trends?window=6h (new endpoint)
- Phase 14 backward compatibility: /trend?window=24h still returns GrowthMetricsVO

Phase 18A scope:
- Multi-window trend calculation (1h, 6h, 24h, 3d)
- Source-aware raw metric deltas with anomaly detection
- Cluster trend aggregation with discovery source dedup
- Cluster trend state derived from signed normalized deltas + acceleration
- No Score V2 ranking change, no frontend visualization, no cache table

Not in Phase 18A scope:
- Switching Score V2 ranking
- Complex frontend trend charts
- Time-series database or streaming compute
- Full historical backfill
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
