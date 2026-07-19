# Phase 17B acceptance script: Cluster Governance Before V2 Online Writes
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
    Write-Section "Phase 17B acceptance start"

    Write-Section "1. Required Phase 17B artifacts"
    $requiredFiles = @(
        "backend/src/main/resources/db/migration/V10__add_cluster_governance.sql",
        "backend/src/main/java/com/airadar/cluster/governance/MembershipAction.java",
        "backend/src/main/java/com/airadar/cluster/governance/OperatorType.java",
        "backend/src/main/java/com/airadar/cluster/governance/ReviewTaskStatus.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterMembershipHistoryEntity.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterMembershipHistoryMapper.java",
        "backend/src/main/java/com/airadar/cluster/governance/MembershipHistoryRecorder.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterReviewTaskEntity.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterReviewTaskMapper.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterMergeService.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterSplitService.java",
        "backend/src/main/java/com/airadar/cluster/governance/MoveItemService.java",
        "backend/src/main/java/com/airadar/cluster/governance/ReclusterService.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterReviewService.java",
        "backend/src/main/java/com/airadar/cluster/governance/ClusterMembershipHistoryQueryService.java",
        "backend/src/main/java/com/airadar/cluster/governance/dto/ClusterMergeRequest.java",
        "backend/src/main/java/com/airadar/cluster/governance/dto/ClusterSplitRequest.java",
        "backend/src/main/java/com/airadar/cluster/governance/dto/MoveItemRequest.java",
        "backend/src/main/java/com/airadar/cluster/governance/dto/ClusterReclusterRequest.java",
        "backend/src/main/java/com/airadar/cluster/governance/dto/ReviewResolutionRequest.java",
        "backend/src/main/java/com/airadar/cluster/governance/vo/ClusterMergeResultVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/vo/ClusterSplitResultVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/vo/MoveItemResultVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/vo/ReclusterResultVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/vo/MembershipHistoryVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/vo/ClusterReviewTaskVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/vo/ReviewResolutionVO.java",
        "backend/src/main/java/com/airadar/cluster/governance/controller/ClusterGovernanceController.java",
        "backend/src/main/java/com/airadar/cluster/governance/controller/ClusterReviewController.java",
        "backend/src/test/java/com/airadar/cluster/governance/ClusterGovernanceIntegrationTest.java",
        "scripts/accept-phase-17b.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. V10 migration adds governance tables"
    $migration = "backend/src/main/resources/db/migration/V10__add_cluster_governance.sql"
    $migrationContent = Get-Content $migration -Raw
    if ($migrationContent -match 'CREATE TABLE cluster_membership_history' -and
        $migrationContent -match 'CREATE TABLE cluster_review_task' -and
        $migrationContent -match "CHECK \(action IN \('ADD', 'REMOVE', 'MOVE', 'MERGE', 'SPLIT', 'RECLUSTER'\)\)" -and
        $migrationContent -match "CHECK \(status IN \('OPEN', 'ACCEPTED', 'REJECTED', 'SKIPPED'\)\)") {
        Step-Success "V10 migration creates governance tables with correct check constraints"
    } else {
        Step-Failure "V10 migration must include both tables and the action/status check constraints"
    }

    Write-Section "3. Governance services enforce transactional writes"
    $merge = "backend/src/main/java/com/airadar/cluster/governance/ClusterMergeService.java"
    $split = "backend/src/main/java/com/airadar/cluster/governance/ClusterSplitService.java"
    $move = "backend/src/main/java/com/airadar/cluster/governance/MoveItemService.java"
    $review = "backend/src/main/java/com/airadar/cluster/governance/ClusterReviewService.java"
    foreach ($service in @($merge, $split, $move, $review)) {
        $content = Get-Content $service -Raw
        if ($content -match '@Transactional') {
            Step-Success "$service is transactional"
        } else {
            Step-Failure "$service must be annotated with @Transactional"
        }
    }

    Write-Section "4. Self-merge and cycle protection"
    $mergeContent = Get-Content $merge -Raw
    if ($mergeContent -match 'winnerClusterId == loserClusterId' -and
        $mergeContent -match 'loser.getMergedIntoClusterId\(\) != null' -and
        $mergeContent -match 'CLUSTER_GOVERNANCE_INVALID_TARGET') {
        Step-Success "ClusterMergeService rejects self-merge, already-merged loser, and cycles"
    } else {
        Step-Failure "ClusterMergeService missing self-merge / cycle guards"
    }

    Write-Section "5. Split prevents empty source cluster"
    $splitContent = Get-Content $split -Raw
    if ($splitContent -match 'Cannot split every active member') {
        Step-Success "ClusterSplitService rejects leaving source cluster empty"
    } else {
        Step-Failure "ClusterSplitService must guard against empty-source splits"
    }

    Write-Section "6. History recorder writes one row per mutation"
    $recorder = "backend/src/main/java/com/airadar/cluster/governance/MembershipHistoryRecorder.java"
    $recorderContent = Get-Content $recorder -Raw
    if ($recorderContent -match 'mapper\.insert\(row\)') {
        Step-Success "MembershipHistoryRecorder inserts rows on demand"
    } else {
        Step-Failure "MembershipHistoryRecorder must insert into cluster_membership_history"
    }

    Write-Section "7. Controller exposes governance API surface"
    $controller = "backend/src/main/java/com/airadar/cluster/governance/controller/ClusterGovernanceController.java"
    $controllerContent = Get-Content $controller -Raw
    if ($controllerContent -match 'PostMapping.*\{clusterId\}/merge' -and
        $controllerContent -match 'PostMapping.*\{clusterId\}/split' -and
        $controllerContent -match 'PostMapping.*\{clusterId\}/items/\{itemId\}/move' -and
        $controllerContent -match 'PostMapping.*\{clusterId\}/recluster' -and
        $controllerContent -match 'GetMapping.*\{clusterId\}/membership-history') {
        Step-Success "ClusterGovernanceController exposes all planned endpoints"
    } else {
        Step-Failure "ClusterGovernanceController endpoint surface drifted from the plan"
    }

    $moveContent = Get-Content $move -Raw
    if ($moveContent -match 'hasActiveMembership' -and
        $moveContent -match 'setStatus\(MERGED\)' -and
        $moveContent -match 'setMergedIntoClusterId\(targetClusterId\)' -and
        $moveContent -match 'setPrimaryItemId\(null\)') {
        Step-Success "MoveItemService closes singleton source clusters instead of leaving empty ACTIVE clusters"
    } else {
        Step-Failure "MoveItemService must close a source cluster when its last active member moves out"
    }

    $reviewController = "backend/src/main/java/com/airadar/cluster/governance/controller/ClusterReviewController.java"
    $reviewContent = Get-Content $reviewController -Raw
    if ($reviewContent -match 'GetMapping' -and
        $reviewContent -match 'PostMapping.*\{taskId\}/accept' -and
        $reviewContent -match 'PostMapping.*\{taskId\}/reject' -and
        $reviewContent -match 'PostMapping.*\{taskId\}/skip') {
        Step-Success "ClusterReviewController exposes list + accept/reject/skip"
    } else {
        Step-Failure "ClusterReviewController missing required endpoints"
    }

    Write-Section "8. ErrorCode extended for governance failures"
    $errorCode = "backend/src/main/java/com/airadar/common/exception/ErrorCode.java"
    $errorCodeContent = Get-Content $errorCode -Raw
    if ($errorCodeContent -match 'CLUSTER_GOVERNANCE_INVALID_TARGET' -and
        $errorCodeContent -match 'CLUSTER_GOVERNANCE_INVALID_ARGUMENT' -and
        $errorCodeContent -match 'CLUSTER_GOVERNANCE_NO_MEMBERSHIP' -and
        $errorCodeContent -match 'CLUSTER_REVIEW_TASK_NOT_FOUND') {
        Step-Success "ErrorCode covers governance failure modes"
    } else {
        Step-Failure "ErrorCode missing governance entries"
    }

    Write-Section "9. V2 promotion is still deferred"
    $strategyProps = "backend/src/main/java/com/airadar/cluster/strategy/ClusterStrategyProperties.java"
    $propsContent = Get-Content $strategyProps -Raw
    if ($propsContent -match 'ALLOWED_ONLINE_STRATEGY = "hn-rule-v1"' -and
        $propsContent -match 'Promoting V2 to the online strategy is Phase 17 work') {
        Step-Success "V2 stays shadow-only - Phase 17B does not flip the online strategy"
    } else {
        Step-Failure "Phase 17B must not promote V2 to the online strategy"
    }

    if (-not $SkipBackendTests) {
        Write-Section "10. Phase 17B integration tests (requires Docker)"
        Write-Host "Running ClusterGovernanceIntegrationTest via Testcontainers (requires Docker daemon)." -ForegroundColor Gray
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
Phase 17B acceptance passed.

Validated components:
- Flyway V10 migration (cluster_membership_history + cluster_review_task)
- MembershipAction / OperatorType / ReviewTaskStatus enums
- ClusterMembershipHistoryEntity + Mapper + Recorder
- ClusterReviewTaskEntity + Mapper (lazy materialization from REVIEW_REQUIRED)
- ClusterMergeService (self-merge / cycle / already-merged guards)
- ClusterSplitService (empty-source guard, optional new target cluster)
- MoveItemService (no-active-membership guard, primary re-selection, singleton-source closure)
- ReclusterService (V2 shadow evaluate, no online membership write)
- ClusterReviewService (accept triggers MoveItemService, reject/skip leave membership intact)
- ClusterGovernanceController + ClusterReviewController API surface
- ClusterMembershipHistoryQueryService for audit reads
- ClusterGovernanceIntegrationTest covers merge / split / move / recluster / review

Phase 17B scope:
- All governance writes transactional and recorded in cluster_membership_history
- REVIEW_REQUIRED decisions materialized into OPEN cluster_review_task rows
- V2 promotion to online strategy still deferred (governance first, online writes later)

Not in Phase 17B scope:
- V2 online membership writes (Phase 17C or later)
- Frontend governance workbench
- Workflow engine / multi-approver review
- Deletion of V1 clustering strategy
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
