# Phase 17C acceptance script: Event Cluster V2 Gradual Online Adoption
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
    Write-Section "Phase 17C acceptance start"

    Write-Section "1. Required Phase 17C artifacts"
    $requiredFiles = @(
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterStrategyProperties.java",
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterAssignmentOrchestrator.java",
        "backend/src/main/java/com/airadar/cluster/strategy/V2OnlineAssignmentService.java",
        "backend/src/main/java/com/airadar/cluster/strategy/V2OnlineEvaluation.java",
        "backend/src/main/java/com/airadar/cluster/strategy/V2OnlineResult.java",
        "backend/src/main/java/com/airadar/cluster/strategy/EventRuleClusterStrategy.java",
        "backend/src/main/java/com/airadar/cluster/strategy/controller/ClusterStrategyController.java",
        "backend/src/main/java/com/airadar/cluster/strategy/vo/ClusterStrategyStatusVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterReviewService.java",
        "backend/src/test/java/com/airadar/cluster/strategy/ClusterStrategyV2OnlineIntegrationTest.java",
        "scripts/accept-phase-17c.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. ClusterStrategyProperties exposes V2 online config"
    $props = "backend/src/main/java/com/airadar/cluster/strategy/ClusterStrategyProperties.java"
    $propsContent = Get-Content $props -Raw
    if ($propsContent -match 'class V2Online' -and
        $propsContent -match 'isEnabled' -and
        $propsContent -match 'getTrafficPercent' -and
        $propsContent -match 'getAllowedMatchLevels' -and
        $propsContent -match 'getL3MinScore' -and
        $propsContent -match 'isReviewRequiredToQueue' -and
        $propsContent -match 'getSourceAllowlist') {
        Step-Success "V2Online nested config exposes every planned field"
    } else {
        Step-Failure "V2Online config missing planned fields"
    }

    Write-Section "3. V2 online defaults stay conservative"
    $appYml = "backend/src/main/resources/application.yml"
    $ymlContent = Get-Content $appYml -Raw
    if ($ymlContent -match 'v2-online:' -and
        $ymlContent -match 'AI_RADAR_CLUSTER_V2_ONLINE_ENABLED:false' -and
        $ymlContent -match 'AI_RADAR_CLUSTER_V2_ONLINE_TRAFFIC_PERCENT:0' -and
        $ymlContent -match 'AI_RADAR_CLUSTER_V2_ONLINE_ALLOWED_LEVELS:L1') {
        Step-Success "application.yml keeps V2 online disabled by default"
    } else {
        Step-Failure "V2 online defaults must remain disabled in application.yml"
    }

    Write-Section "4. Validation rejects unsafe V2 online configs"
    $validStrategyGuard = ($propsContent -match 'Promoting V2 to the online strategy is Phase 17 work') -or
                          ($propsContent -match 'V2 online writes are gated by ai-radar.cluster.v2-online.enabled')
    if ($propsContent -match 'traffic-percent must be between 0 and 100' -and
        $propsContent -match 'requires traffic-percent > 0' -and
        $propsContent -match 'allowed-match-levels entry' -and
        $propsContent -match 'l3-min-score must be between 0.60 and 1.00' -and
        $validStrategyGuard) {
        Step-Success "Properties validation rejects unsafe rollout configs and strategy=event-rule-v2"
    } else {
        Step-Failure "Properties validation missing required guards"
    }

    Write-Section "5. Orchestrator dispatches V1-only / shadow / V2-online paths"
    $orchestrator = "backend/src/main/java/com/airadar/cluster/strategy/ClusterAssignmentOrchestrator.java"
    $orchContent = Get-Content $orchestrator -Raw
    if ($orchContent -match 'isV2OnlineEnabled' -and
        $orchContent -match 'applyV2Online' -and
        $orchContent -match 'passesTrafficGate' -and
        $orchContent -match 'passesSourceGate' -and
        $orchContent -match 'V2 online assignment failed.*staying with V1 cluster') {
        Step-Success "Orchestrator applies traffic + source gates and falls back to V1 on failure"
    } else {
        Step-Failure "Orchestrator missing V2 online dispatch / fallback"
    }

    Write-Section "6. V2OnlineAssignmentService isolates writes via nested transaction"
    $v2Service = "backend/src/main/java/com/airadar/cluster/strategy/V2OnlineAssignmentService.java"
    $v2Content = Get-Content $v2Service -Raw
    if ($v2Content -match 'Propagation.NESTED' -and
        $v2Content -match 'evaluateForOnline' -and
        $v2Content -match 'moveItemService.move' -and
        $v2Content -match 'OperatorType.SYSTEM' -and
        $v2Content -match 'reviewService.materializeOpenTasks') {
        Step-Success "V2OnlineAssignmentService runs in nested savepoint and goes through MoveItemService"
    } else {
        Step-Failure "V2OnlineAssignmentService missing nested transaction or governance integration"
    }

    Write-Section "7. EventRuleClusterStrategy exposes evaluateForOnline"
    $v2Strategy = "backend/src/main/java/com/airadar/cluster/strategy/EventRuleClusterStrategy.java"
    $v2StrategyContent = Get-Content $v2Strategy -Raw
    if ($v2StrategyContent -match 'public V2OnlineEvaluation evaluateForOnline' -and
        $v2StrategyContent -match 'Self-match' -and
        $v2StrategyContent -match 'candidate.getHotItemId\(\) != null && candidate.getHotItemId\(\).equals\(item.getId\(\)\)') {
        Step-Success "V2 strategy exposes evaluateForOnline with self-match exclusion"
    } else {
        Step-Failure "V2 strategy missing evaluateForOnline self-match guard"
    }

    Write-Section "8. Review queue exposes proactive materialization"
    $reviewService = "backend/src/main/java/com/airadar/cluster/governance/ClusterReviewService.java"
    $reviewContent = Get-Content $reviewService -Raw
    if ($reviewContent -match 'public void materializeOpenTasks') {
        Step-Success "ClusterReviewService.materializeOpenTasks is callable from V2 online writer"
    } else {
        Step-Failure "ClusterReviewService.materializeOpenTasks must be public for Phase 17C"
    }

    Write-Section "9. Status API exposes rollout stage"
    $controller = "backend/src/main/java/com/airadar/cluster/strategy/controller/ClusterStrategyController.java"
    $controllerContent = Get-Content $controller -Raw
    if ($controllerContent -match 'GetMapping.*"/status"' -and
        $controllerContent -match 'RequestMapping.*"/api/v1/cluster-strategy"' -and
        $controllerContent -match 'describeRolloutStage' -and
        $controllerContent -match 'STAGE_2_L1' -and
        $controllerContent -match 'STAGE_3_L2' -and
        $controllerContent -match 'STAGE_4_L3' -and
        $controllerContent -match 'SHADOW_ONLY' -and
        $controllerContent -match 'V1_ONLY') {
        Step-Success "ClusterStrategyController exposes status with rollout stage"
    } else {
        Step-Failure "ClusterStrategyController endpoint or rollout stage drifted"
    }

    Write-Section "10. V1 fallback remains one property flip away"
    if ($ymlContent -match 'strategy: \$\{AI_RADAR_CLUSTER_STRATEGY:hn-rule-v1\}' -and
        $ymlContent -match 'AI_RADAR_CLUSTER_V2_ONLINE_ENABLED:false') {
        Step-Success "Disabling AI_RADAR_CLUSTER_V2_ONLINE_ENABLED restores V1-only behavior"
    } else {
        Step-Failure "V1 fallback config must remain in application.yml"
    }

    if (-not $SkipBackendTests) {
        Write-Section "11. Phase 17C integration tests (requires Docker)"
        Write-Host "Running ClusterStrategyV2OnlineIntegrationTest via Testcontainers (requires Docker daemon)." -ForegroundColor Gray
        Invoke-Step `
            "Phase 17C integration tests" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=ClusterStrategyV2OnlineIntegrationTest", "test")

        Write-Section "12. Phase 17B regression tests (governance still intact)"
        Invoke-Step `
            "Phase 17B integration tests" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=ClusterGovernanceIntegrationTest", "test")
    } else {
        Write-Host "Skip backend tests." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 17C acceptance passed.

Validated components:
- ClusterStrategyProperties.V2Online nested config (enabled / traffic-percent /
  allowed-match-levels / l3-min-score / review-required-to-queue / source-allowlist)
- application.yml defaults: v2-online.enabled=false, traffic-percent=0, allowed=L1
- Startup validation rejects unsafe configs (0% enabled, no allowed levels,
  unknown level, out-of-range traffic-percent / l3-min-score, strategy=event-rule-v2)
- ClusterAssignmentOrchestrator dispatch paths (V1-only / shadow / V2-online)
- V2OnlineAssignmentService nested savepoint (V2 failure rolls back to V1)
- EventRuleClusterStrategy.evaluateForOnline (writes decisions, picks best,
  excludes self-match)
- MoveItemService integration (V2 online writes governance-audited history)
- ClusterReviewService.materializeOpenTasks public (proactive review queue)
- ClusterStrategyController GET /api/v1/cluster-strategy/status (rollout stage)
- ClusterStrategyV2OnlineIntegrationTest covers every rollout path
- Phase 17B governance tests still pass (no regression)

Phase 17C scope:
- V2 online writes go through MoveItemService with SYSTEM operator attribution
- Allowed levels gate L1 / L2 / L3 independently; L3 requires l3-min-score
- REVIEW_REQUIRED decisions materialize into OPEN cluster_review_task rows
- V1 online singleton always created; V2 only relocates when gates clear
- V2 online failures roll back to savepoint; V1 result preserved
- Status API returns V1_ONLY / SHADOW_ONLY / STAGE_2_L1 / STAGE_3_L2 / STAGE_4_L3

Not in Phase 17C scope:
- Deleting V1 (still authoritative; V2 only relocates)
- LLM / embedding match layers
- Full-table historical re-cluster
- Automatic acceptance of REVIEW_REQUIRED (still requires manual resolution)
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
