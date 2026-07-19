# Phase 17A: real-data sample export
#
# Exports recent hot_item + hot_cluster + hot_score (V1 and V2) rows from the
# local postgres container into JSONL files under evaluation/samples/.
#
# Outputs:
#   evaluation/samples/real-samples-{yyyyMMdd}.jsonl
#       One hot_item per line, with its current cluster id and the latest
#       V1 and V2 score snapshots attached.
#
#   evaluation/samples/candidate-pairs-{yyyyMMdd}.jsonl
#       JSONL annotation templates that the annotator should label by filling
#       expectation and category. Sources of candidates:
#         - items in the same cluster (sanity check that V1 already merged)
#         - items sharing the same canonical domain but in different clusters
#         - items whose normalized title substring overlaps
#
# Usage:
#   .\scripts\export-evaluation-samples.ps1                       # default 7-day window
#   .\scripts\export-evaluation-samples.ps1 -Days 14              # wider window
#   .\scripts\export-evaluation-samples.ps1 -Container my-pg      # non-default container
#
# Requirements:
#   - Docker desktop / engine running
#   - ai-radar-postgres-1 (or override via -Container) reachable

[CmdletBinding()]
param(
    [int]$Days = 7,
    [string]$Container = "ai-radar-postgres-1",
    [string]$DbUser = "ai_radar",
    [string]$DbName = "ai_radar",
    [string]$OutDir = "evaluation/samples"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try {
    $stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd")
    $samplesPath = Join-Path $OutDir "real-samples-$stamp.jsonl"
    $pairsPath = Join-Path $OutDir "candidate-pairs-$stamp.jsonl"

    if (-not (Test-Path $OutDir)) {
        New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
    }

    Write-Host "=== Phase 17A sample export ===" -ForegroundColor Cyan
    Write-Host "Window: last $Days days"
    Write-Host "Container: $Container"
    Write-Host "Output dir: $OutDir"
    Write-Host ""

    # Sanity check: container is up.
    $containerState = docker inspect -f '{{.State.Running}}' $Container 2>$null
    if ($containerState -ne "true") {
        throw "Container $Container is not running. Start it with: docker compose up -d postgres"
    }

    # --- Sample query: hot_item + cluster + V1/V2 scores ---
    # jsonb_agg with scores filtered by scoring_version. Each hot_item row becomes
    # one JSONB object that psql prints as a single line when -A -t is set.
    $samplesSql = @"
SET search_path = public;
\COPY (
    WITH recent AS (
        SELECT *
        FROM hot_item
        WHERE last_seen_at >= NOW() - INTERVAL '$Days days'
    ),
    cluster_map AS (
        SELECT hot_item_id, MAX(hot_cluster_id) AS cluster_id
        FROM hot_cluster_item
        WHERE removed_at IS NULL
        GROUP BY hot_item_id
    ),
    score_v1 AS (
        SELECT DISTINCT ON (hot_cluster_id)
            hot_cluster_id, total_score, score_components, calculated_at
        FROM hot_score
        WHERE scoring_version = 'hn-score-v1'
        ORDER BY hot_cluster_id, calculated_at DESC
    ),
    score_v2 AS (
        SELECT DISTINCT ON (hot_cluster_id)
            hot_cluster_id, total_score, score_components, calculated_at
        FROM hot_score
        WHERE scoring_version = 'cross-source-score-v2'
        ORDER BY hot_cluster_id, calculated_at DESC
    )
    SELECT jsonb_build_object(
        'hotItemId', r.id,
        'sourceType', r.source_type,
        'externalId', r.external_id,
        'itemType', r.item_type,
        'title', r.title,
        'summary', r.summary,
        'sourceUrl', r.source_url,
        'author', r.author,
        'publishedAt', r.published_at,
        'firstSeenAt', r.first_seen_at,
        'lastSeenAt', r.last_seen_at,
        'tags', r.tags,
        'metrics', r.metrics,
        'clusterId', cm.cluster_id,
        'scoreV1', CASE WHEN s1.total_score IS NULL THEN NULL
                        ELSE jsonb_build_object('total', s1.total_score, 'components', s1.score_components, 'calculatedAt', s1.calculated_at) END,
        'scoreV2', CASE WHEN s2.total_score IS NULL THEN NULL
                        ELSE jsonb_build_object('total', s2.total_score, 'components', s2.score_components, 'calculatedAt', s2.calculated_at) END
    ) AS line
    FROM recent r
    LEFT JOIN cluster_map cm ON cm.hot_item_id = r.id
    LEFT JOIN score_v1 s1 ON s1.hot_cluster_id = cm.cluster_id
    LEFT JOIN score_v2 s2 ON s2.hot_cluster_id = cm.cluster_id
    ORDER BY r.id
) TO STDOUT WITH (FORMAT text)
"@

    Write-Host "[1/3] Exporting samples to $samplesPath" -ForegroundColor Yellow
    docker exec -i $Container psql -U $DbUser -d $DbName -A -t -c $samplesSql | Set-Content -Path $samplesPath -Encoding utf8
    $sampleCount = (Get-Content $samplesPath | Where-Object { $_.Trim().Length -gt 0 }).Count
    Write-Host "[OK] $sampleCount samples exported" -ForegroundColor Green

    # --- Candidate pairs: same domain across clusters + same-cluster sanity ---
    # Pairs are emitted unordered (a,b) with a < b. The JSON shape matches
    # EvaluationSampleImportService#importClusterPairs after expectation/category
    # are filled by a human reviewer.
    $pairsSql = @"
SET search_path = public;
\COPY (
    WITH recent AS (
        SELECT id, source_type, external_id, title, source_url,
               SUBSTRING(source_url FROM '(?:https?://)?(?:www\.)?([^/]+)') AS domain
        FROM hot_item
        WHERE last_seen_at >= NOW() - INTERVAL '$Days days'
    ),
    cluster_map AS (
        SELECT hot_item_id, MAX(hot_cluster_id) AS cluster_id
        FROM hot_cluster_item
        WHERE removed_at IS NULL
        GROUP BY hot_item_id
    ),
    same_cluster_pairs AS (
        SELECT a.id AS a, b.id AS b, 'SAME_CLUSTER' AS reason
        FROM recent a
        JOIN cluster_map ca ON ca.hot_item_id = a.id
        JOIN recent b ON b.id > a.id
        JOIN cluster_map cb ON cb.hot_item_id = b.id
        WHERE ca.cluster_id = cb.cluster_id
          AND ca.cluster_id IS NOT NULL
    ),
    same_domain_pairs AS (
        SELECT a.id AS a, b.id AS b, 'SAME_DOMAIN_DIFFERENT_CLUSTER' AS reason
        FROM recent a
        JOIN recent b ON b.id > a.id
        LEFT JOIN cluster_map ca ON ca.hot_item_id = a.id
        LEFT JOIN cluster_map cb ON cb.hot_item_id = b.id
        WHERE a.domain IS NOT NULL
          AND a.domain = b.domain
          AND (ca.cluster_id IS DISTINCT FROM cb.cluster_id)
    ),
    title_overlap_pairs AS (
        SELECT a.id AS a, b.id AS b, 'TITLE_OVERLAP' AS reason
        FROM recent a
        JOIN recent b ON b.id > a.id
        WHERE LENGTH(a.title) >= 8
          AND LENGTH(b.title) >= 8
          AND (
              a.title ILIKE '%' || LEFT(b.title, 8) || '%'
              OR b.title ILIKE '%' || LEFT(a.title, 8) || '%'
          )
    ),
    all_pairs AS (
        SELECT * FROM same_cluster_pairs
        UNION
        SELECT * FROM same_domain_pairs
        UNION
        SELECT * FROM title_overlap_pairs
    )
    SELECT jsonb_build_object(
        'pairKey', reason || '-' || p.a || '-' || p.b,
        'itemA', jsonb_build_object(
            'hotItemId', ia.id,
            'sourceType', ia.source_type,
            'externalId', ia.external_id,
            'title', ia.title,
            'sourceUrl', ia.source_url
        ),
        'itemB', jsonb_build_object(
            'hotItemId', ib.id,
            'sourceType', ib.source_type,
            'externalId', ib.external_id,
            'title', ib.title,
            'sourceUrl', ib.source_url
        ),
        'candidateReason', p.reason,
        'expectation', NULL,
        'category', NULL,
        'rationale', NULL,
        'annotator', NULL
    ) AS line
    FROM all_pairs p
    JOIN recent ia ON ia.id = p.a
    JOIN recent ib ON ib.id = p.b
    ORDER BY p.reason, p.a, p.b
) TO STDOUT WITH (FORMAT text)
"@

    Write-Host "[2/3] Exporting candidate pairs to $pairsPath" -ForegroundColor Yellow
    docker exec -i $Container psql -U $DbUser -d $DbName -A -t -c $pairsSql | Set-Content -Path $pairsPath -Encoding utf8
    $pairCount = (Get-Content $pairsPath | Where-Object { $_.Trim().Length -gt 0 }).Count
    Write-Host "[OK] $pairCount candidate pairs exported" -ForegroundColor Green

    Write-Host "[3/3] Reminder: $OutDir is gitignored. Do not commit raw samples." -ForegroundColor Yellow

    Write-Host ""
    Write-Host "=== Export complete ===" -ForegroundColor Cyan
    Write-Host "Next steps for the annotator:"
    Write-Host "  1. Open $pairsPath"
    Write-Host "  2. For each pair, fill in 'expectation' (MUST_MERGE / MUST_NOT_MERGE / REVIEW_IF_AMBIGUOUS) and 'category'"
    Write-Host "  3. Save as $OutDir/labeled-pairs-$stamp.jsonl for import via EvaluationSampleImportService"
}
catch {
    Write-Host "`nError: $_" -ForegroundColor Red
    exit 1
}
finally {
    Pop-Location
}
