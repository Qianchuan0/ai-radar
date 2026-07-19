# Phase 17A acceptance script: real-data evaluation closed loop
#
# Verifies that the Phase 17A plan delivers:
#   - V9 migration extending evaluation_case.case_type
#   - Payload validators for the two new case types
#   - EvaluationSampleImportService for JSONL annotations
#   - RealDataClusterEvaluationService (V1 + V2)
#   - RankingEvaluationService (V1 + V2)
#   - EvaluationReportWriter writing 4 JSONs + summary.md
#   - Integration test covering the end-to-end loop
#
# Usage:
#   .\scripts\accept-phase-17a.ps1                 # default
#   .\scripts\accept-phase-17a.ps1 -SkipIntegration  # skip Testcontainers steps
param(
    [switch]$SkipIntegration = $false
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
    Write-Section "Phase 17A acceptance start"

    Write-Section "1. Required Phase 17A artifacts"
    $requiredFiles = @(
        "backend/src/main/resources/db/migration/V9__extend_evaluation_case_types.sql",
        "backend/src/main/java/com/airadar/evaluation/model/EvaluationCaseType.java",
        "backend/src/main/java/com/airadar/evaluation/service/verifier/EvaluationPayloadValidator.java",
        "backend/src/main/java/com/airadar/evaluation/service/verifier/ClusterPairPayloadValidator.java",
        "backend/src/main/java/com/airadar/evaluation/service/verifier/RankingRelevancePayloadValidator.java",
        "backend/src/main/java/com/airadar/evaluation/service/EvaluationSampleImportService.java",
        "backend/src/main/java/com/airadar/evaluation/realtimedata/package-info.java",
        "backend/src/main/java/com/airadar/evaluation/realtimedata/RealDataClusterEvaluationService.java",
        "backend/src/main/java/com/airadar/evaluation/realtimedata/ClusterPairEvaluationReport.java",
        "backend/src/main/java/com/airadar/evaluation/realtimedata/RankingEvaluationService.java",
        "backend/src/main/java/com/airadar/evaluation/realtimedata/RankingEvaluationReport.java",
        "backend/src/main/java/com/airadar/evaluation/realtimedata/EvaluationReportWriter.java",
        "scripts/export-evaluation-samples.ps1",
        "scripts/accept-phase-17a.ps1"
    )
    foreach ($file in $requiredFiles) {
        if (Test-Path $file) {
            Step-Success "File exists: $file"
        } else {
            Step-Failure "Missing file: $file"
        }
    }

    Write-Section "2. V9 migration extends case_type"
    $migration = "backend/src/main/resources/db/migration/V9__extend_evaluation_case_types.sql"
    $migrationContent = Get-Content $migration -Raw
    if ($migrationContent -match 'CLUSTER_PAIR_EXPECTATION' -and
        $migrationContent -match 'RANKING_RELEVANCE_EXPECTATION' -and
        $migrationContent -match 'DROP CONSTRAINT IF EXISTS ck_evaluation_case_type') {
        Step-Success "V9 migration extends case_type with the two new values"
    } else {
        Step-Failure "V9 migration must extend case_type for the two new values"
    }

    Write-Section "3. EvaluationCaseType enum extended"
    $enumFile = "backend/src/main/java/com/airadar/evaluation/model/EvaluationCaseType.java"
    $enumContent = Get-Content $enumFile -Raw
    if ($enumContent -match 'CLUSTER_PAIR_EXPECTATION' -and
        $enumContent -match 'RANKING_RELEVANCE_EXPECTATION') {
        Step-Success "Enum exposes both new case types"
    } else {
        Step-Failure "Enum must expose both new case types"
    }

    Write-Section "4. Evaluation service validates new case payloads on create"
    $serviceFile = "backend/src/main/java/com/airadar/evaluation/service/EvaluationService.java"
    $serviceContent = Get-Content $serviceFile -Raw
    if ($serviceContent -match 'EvaluationPayloadValidator' -and
        $serviceContent -match 'payloadValidators\.get\(request\.caseType\(\)') {
        Step-Success "EvaluationService.createCase applies validator when present"
    } else {
        Step-Failure "EvaluationService must look up validator by case type in createCase"
    }

    Write-Section "5. Import service is idempotent and isolated"
    $importFile = "backend/src/main/java/com/airadar/evaluation/service/EvaluationSampleImportService.java"
    $importContent = Get-Content $importFile -Raw
    if ($importContent -match 'caseExists' -and
        $importContent -match 'MAX_ERRORS_REPORTED' -and
        $importContent -match 'importClusterPairs' -and
        $importContent -match 'importRankingRelevance') {
        Step-Success "Import service has idempotent skip + failure isolation + both methods"
    } else {
        Step-Failure "Import service missing required behavior"
    }

    Write-Section "6. Cluster eval supports both strategies without truncating"
    $clusterEval = "backend/src/main/java/com/airadar/evaluation/realtimedata/RealDataClusterEvaluationService.java"
    $clusterEvalContent = Get-Content $clusterEval -Raw
    if ($clusterEvalContent -match 'RuleBasedClusterService\.RULE_VERSION' -and
        $clusterEvalContent -match 'EventRuleClusterStrategy\.RULE_VERSION' -and
        $clusterEvalContent -notmatch 'TRUNCATE TABLE' -and
        $clusterEvalContent -notmatch 'jdbcTemplate') {
        Step-Success "Cluster evaluator covers V1 + V2 and never truncates"
    } else {
        Step-Failure "Cluster evaluator must cover V1 + V2 without truncating"
    }

    Write-Section "7. Ranking eval computes the documented metrics"
    $rankingReport = "backend/src/main/java/com/airadar/evaluation/realtimedata/RankingEvaluationReport.java"
    $rankingReportContent = Get-Content $rankingReport -Raw
    if ($rankingReportContent -match 'getPrecisionAtN' -and
        $rankingReportContent -match 'getNdcgAtN' -and
        $rankingReportContent -match 'getTopNNoiseRate' -and
        $rankingReportContent -match 'getMajorEventMissRate' -and
        $rankingReportContent -match 'getRankingDiffVsV1TopN') {
        Step-Success "Ranking report exposes all required metrics"
    } else {
        Step-Failure "Ranking report must expose all required metrics"
    }

    Write-Section "8. Report writer produces the four JSONs + summary"
    $writer = "backend/src/main/java/com/airadar/evaluation/realtimedata/EvaluationReportWriter.java"
    $writerContent = Get-Content $writer -Raw
    if ($writerContent -match 'cluster-v1\.json' -and
        $writerContent -match 'cluster-v2\.json' -and
        $writerContent -match 'ranking-v1\.json' -and
        $writerContent -match 'ranking-v2\.json' -and
        $writerContent -match 'summary\.md') {
        Step-Success "Writer emits all 4 JSONs + Markdown summary"
    } else {
        Step-Failure "Writer must emit all 4 JSONs + Markdown summary"
    }

    Write-Section "9. evaluation/samples/ and evaluation/reports/ are gitignored"
    $gitignore = Get-Content ".gitignore" -Raw
    if ($gitignore -match 'evaluation/samples/' -and
        $gitignore -match 'evaluation/reports/') {
        Step-Success "Sample and report dirs are gitignored"
    } else {
        Step-Failure ".gitignore must list evaluation/samples/ and evaluation/reports/"
    }

    Write-Section "10. Backend compiles"
    Invoke-Step `
        "mvn compile" `
        (Join-Path $ProjectRoot "backend") `
        ".\mvnw.cmd" `
        @("-q", "-DskipTests", "compile")

    Write-Section "11. Phase 17A unit tests"
    Invoke-Step `
        "Phase 17A unit tests" `
        (Join-Path $ProjectRoot "backend") `
        ".\mvnw.cmd" `
        @("-Dtest=ClusterPairPayloadValidatorTest,RankingRelevancePayloadValidatorTest,EvaluationSampleImportServiceTest,RealDataClusterEvaluationServiceTest,RankingEvaluationServiceTest,EvaluationReportWriterTest", "test")

    if (-not $SkipIntegration) {
        Write-Section "12. Phase 17A integration test (requires Docker)"
        Write-Host "Running RealDataEvaluationIntegrationTest via Testcontainers." -ForegroundColor Gray
        Invoke-Step `
            "Phase 17A integration test" `
            (Join-Path $ProjectRoot "backend") `
            ".\mvnw.cmd" `
            @("-Dtest=RealDataEvaluationIntegrationTest", "test")
        $integrationSummary = "- RealDataEvaluationIntegrationTest: end-to-end closed loop validated"
    } else {
        Write-Host "Skip integration tests (-SkipIntegration)." -ForegroundColor Gray
        $integrationSummary = "- RealDataEvaluationIntegrationTest: skipped by -SkipIntegration"
    }

    Write-Section "Acceptance summary"
    Write-Host @"
Phase 17A acceptance passed.

Validated components:
- Flyway V9 migration extending evaluation_case.case_type
- EvaluationCaseType enum: CLUSTER_PAIR_EXPECTATION, RANKING_RELEVANCE_EXPECTATION
- Payload validators for both new case types
- EvaluationSampleImportService: idempotent + failure-isolated JSONL import
- RealDataClusterEvaluationService: V1 + V2 cluster pair eval, no truncation
- RankingEvaluationService: precision@N, NDCG@N, noise rate, major-event miss, V1/V2 diff
- EvaluationReportWriter: 4 JSONs + summary.md
$integrationSummary
- scripts/export-evaluation-samples.ps1: real-data sample export

Phase 17A scope:
- Build real-data evaluation closed loop without changing online strategy
- V1 and V2 are scored on identical labeled expectations
- Maintainer decides whether gates are met before any Phase 17B work starts

Not in Phase 17A scope:
- Switching ai-radar.cluster.strategy
- Switching default ranking scoring version
- LLM judge
- Frontend visualization
"@ -ForegroundColor Green
} catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
