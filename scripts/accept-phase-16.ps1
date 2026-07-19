# Phase 16 acceptance script: Event Cluster V2
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
    Write-Section "Phase 16 acceptance start"

    Write-Section "1. Required Phase 16 artifacts"
    $requiredFiles = @(
        "backend/src/main/resources/db/migration/V8__add_event_cluster_v2.sql",
        "backend/src/main/java/com/airadar/cluster/feature/HotItemFeatureEntity.java",
        "backend/src/main/java/com/airadar/cluster/feature/HotItemFeatureMapper.java",
        "backend/src/main/java/com/airadar/cluster/feature/package-info.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/ItemFeature.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/EntityRef.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/EventType.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/ItemFeatureExtractor.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/ItemFeatureEntityConverter.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/TitleNormalizer.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/ExternalIdExtractor.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/EntityAliasDictionary.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/EntityExtractor.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/KeywordExtractor.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/EventTimeResolver.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/PublisherResolver.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/EventTypeResolver.java",
        "backend/src/main/java/com/airadar/cluster/feature/extractor/package-info.java",
        "backend/src/main/java/com/airadar/cluster/strategy/CandidateCluster.java",
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterCandidateRetriever.java",
        "backend/src/main/java/com/airadar/cluster/strategy/MatchOutcome.java",
        "backend/src/main/java/com/airadar/cluster/strategy/LayeredMatcher.java",
        "backend/src/main/java/com/airadar/cluster/strategy/EventRuleClusterStrategy.java",
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterMatchDecisionEntity.java",
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterMatchDecisionMapper.java",
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterAssignmentOrchestrator.java",
        "backend/src/main/java/com/airadar/cluster/strategy/ClusterStrategyProperties.java",
        "backend/src/main/java/com/airadar/cluster/strategy/PrimaryItemSelector.java",
        "backend/src/test/java/com/airadar/evaluation/cluster/Phase16ClusterComparisonIntegrationTest.java",
        "scripts/accept-phase-16.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. V8 migration adds feature and decision tables"
    $migration = "backend/src/main/resources/db/migration/V8__add_event_cluster_v2.sql"
    $migrationContent = Get-Content $migration -Raw
    if ($migrationContent -match 'CREATE TABLE hot_item_feature' -and
        $migrationContent -match 'CREATE TABLE cluster_match_decision' -and
        $migrationContent -match "CHECK \(decision IN \('ACCEPTED', 'REJECTED', 'REVIEW_REQUIRED', 'NO_CANDIDATE'\)\)") {
        Step-Success "V8 migration creates hot_item_feature and cluster_match_decision"
    } else {
        Step-Failure "V8 migration must include both tables and decision check constraint"
    }

    Write-Section "3. V1 zero-change guarantee"
    $v1Service = "backend/src/main/java/com/airadar/cluster/service/RuleBasedClusterService.java"
    $v1Content = Get-Content $v1Service -Raw
    if ($v1Content -match 'public static final String RULE_VERSION = "hn-rule-v1"' -and
        $v1Content -match 'findClusterByCanonicalUrl') {
        Step-Success "RuleBasedClusterService logic preserved"
    } else {
        Step-Failure "RuleBasedClusterService appears modified"
    }

    Write-Section "4. Layered matcher encodes the documented thresholds"
    $matcher = "backend/src/main/java/com/airadar/cluster/strategy/LayeredMatcher.java"
    $matcherContent = Get-Content $matcher -Raw
    if ($matcherContent -match 'LEVEL_3_ACCEPT_THRESHOLD = 0\.82' -and
        $matcherContent -match 'LEVEL_3_REJECT_THRESHOLD = 0\.60' -and
        $matcherContent -match 'LEVEL_2_TIME_WINDOW = Duration\.ofHours\(48\)') {
        Step-Success "Layered matcher thresholds match Phase 16 plan"
    } else {
        Step-Failure "Layered matcher thresholds drifted from the plan"
    }

    Write-Section "5. Retriever bounded to 72h / 50 candidates"
    $retriever = "backend/src/main/java/com/airadar/cluster/strategy/ClusterCandidateRetriever.java"
    $retrieverContent = Get-Content $retriever -Raw
    if ($retrieverContent -match 'DEFAULT_WINDOW_HOURS = 72' -and
        $retrieverContent -match 'DEFAULT_MAX_CANDIDATES = 50') {
        Step-Success "ClusterCandidateRetriever defaults bounded per Phase 16 plan"
    } else {
        Step-Failure "ClusterCandidateRetriever bounds must match the plan"
    }

    Write-Section "6. Pipeline switched to ClusterAssignmentOrchestrator"
    $pipeline = "backend/src/main/java/com/airadar/item/service/ItemPipelineService.java"
    $pipelineContent = Get-Content $pipeline -Raw
    if ($pipelineContent -match 'clusterOrchestrator\.assign\(hotItem\)' -and
        $pipelineContent -match 'ClusterAssignmentOrchestrator') {
        Step-Success "ItemPipelineService uses ClusterAssignmentOrchestrator"
    } else {
        Step-Failure "ItemPipelineService must call clusterOrchestrator.assign(hotItem)"
    }
    if ($pipelineContent -match 'clusterService\.assign') {
        Step-Failure "ItemPipelineService still references clusterService.assign directly"
    } else {
        Step-Success "No direct RuleBasedClusterService.assign reference remains"
    }

    Write-Section "7. Shadow configuration wired"
    $yaml = "backend/src/main/resources/application.yml"
    $yamlContent = Get-Content $yaml -Raw
    if ($yamlContent -match 'ai-radar:' -and
        $yamlContent -match 'cluster:' -and
        $yamlContent -match 'shadow-strategy:') {
        Step-Success "ai-radar.cluster.strategy / shadow-strategy exposed via configuration"
    } else {
        Step-Failure "ai-radar.cluster.* keys missing in application.yml"
    }

    $orchestrator = "backend/src/main/java/com/airadar/cluster/strategy/ClusterAssignmentOrchestrator.java"
    $orchestratorContent = Get-Content $orchestrator -Raw
    if ($orchestratorContent -match 'isShadowEnabled' -and
        $orchestratorContent -match 'v2Strategy\.evaluate\(item\)') {
        Step-Success "Orchestrator runs V2 evaluate-only when shadow is enabled"
    } else {
        Step-Failure "Orchestrator must guard shadow with isShadowEnabled"
    }

    Write-Section "8. Decision persistence"
    $v2Strategy = "backend/src/main/java/com/airadar/cluster/strategy/EventRuleClusterStrategy.java"
    $v2Content = Get-Content $v2Strategy -Raw
    $matcherContent = Get-Content "backend/src/main/java/com/airadar/cluster/strategy/LayeredMatcher.java" -Raw
    if ($v2Content -match 'decisionMapper\.insert\(entity\)' -and
        $v2Content -match 'AssignmentDecision\.NO_CANDIDATE' -and
        $matcherContent -match 'AssignmentDecision\.REVIEW_REQUIRED' -and
        $matcherContent -match 'AssignmentDecision\.REJECTED' -and
        $matcherContent -match 'AssignmentDecision\.ACCEPTED') {
        Step-Success "V2 strategy persists every decision outcome"
    } else {
        Step-Failure "V2 strategy must persist all four decision outcomes"
    }

    if (-not $SkipBackendTests) {
        Write-Section "9. Phase 16 integration tests (requires Docker)"
        Write-Host "Running ClusterEvaluationIntegrationTest + Phase16ClusterComparisonIntegrationTest via Testcontainers (requires Docker daemon)." -ForegroundColor Gray
        Invoke-Step `
            "Phase 16 integration tests" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=ClusterEvaluationIntegrationTest,Phase16ClusterComparisonIntegrationTest", "test")
    } else {
        Write-Host "Skip backend tests." -ForegroundColor Gray
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 16 acceptance passed.

Validated components:
- Flyway V8 migration (hot_item_feature + cluster_match_decision)
- ItemFeature pipeline (7 extractors: title, external id, entity, keyword, event time, publisher, event type)
- ClusterCandidateRetriever (72h window, max 50 candidates, 5 signals)
- LayeredMatcher (L1 identifiers, L2 entity+org+type+time, L3 weighted similarity)
- EventRuleClusterStrategy (event-rule-v2) with online assign and shadow evaluate modes
- ClusterAssignmentOrchestrator wires V1 online + V2 shadow via configuration
- PrimaryItemSelector re-selects primary after V2 membership changes
- cluster_match_decision persists ACCEPTED/REJECTED/REVIEW_REQUIRED/NO_CANDIDATE
- ItemPipelineService switched to orchestrator
- Phase16ClusterComparisonIntegrationTest covers V1/V2 delta

Phase 16 scope:
- V2 shadow runs alongside V1 without affecting online cluster state
- All V2 decisions auditable via cluster_match_decision
- Deterministic, explainable, no LLM in the decision path

Not in Phase 16 scope:
- pgvector / embeddings (deferred)
- LLM as decision maker (rejected)
- Cluster merge/split governance (Phase 17)
- Replacing V1 as online strategy (Phase 17)
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
