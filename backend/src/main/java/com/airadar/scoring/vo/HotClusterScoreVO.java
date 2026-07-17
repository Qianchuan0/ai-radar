package com.airadar.scoring.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Value object exposing a single persisted score for the comparison API.
 *
 * <p>Returned by {@code GET /api/v1/hot-clusters/{id}/scores} so callers can
 * inspect V1 and V2 scores side by side.
 *
 * @param scoringVersion  version tag (e.g. {@code hn-score-v1}, {@code cross-source-score-v2})
 * @param totalScore      weighted total score
 * @param calculatedAt    calculation timestamp
 * @param scoreComponents full component breakdown for explainability
 */
public record HotClusterScoreVO(
        String scoringVersion,
        BigDecimal totalScore,
        Instant calculatedAt,
        JsonNode scoreComponents
) {
}
