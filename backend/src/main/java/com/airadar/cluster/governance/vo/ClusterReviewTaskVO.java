package com.airadar.cluster.governance.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single {@code cluster_review_task} row joined with its underlying
 * {@code cluster_match_decision}.
 */
public record ClusterReviewTaskVO(
        Long id,
        Long clusterMatchDecisionId,
        Long hotItemId,
        Long candidateClusterId,
        String decision,
        BigDecimal matchScore,
        String matchMethod,
        JsonNode matchReason,
        String ruleVersion,
        Instant decidedAt,
        String status,
        String resolutionReason,
        Instant resolvedAt,
        Instant createdAt
) {
}
