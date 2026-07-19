package com.airadar.cluster.governance.vo;

import java.time.Instant;
import java.util.List;

/**
 * Result of {@code POST /api/v1/hot-clusters/{id}/recluster}.
 *
 * <p>{@code decisions} lists the {@code cluster_match_decision} rows the V2
 * shadow run produced. Operators review these and then call merge / split /
 * move to act on them.
 */
public record ReclusterResultVO(
        Instant evaluatedAt,
        int evaluatedItemCount,
        List<ReclusteredDecision> decisions,
        List<Long> historyIds
) {
    public record ReclusteredDecision(
            Long hotItemId,
            Long candidateClusterId,
            String decision,
            String matchMethod,
            String matchScore,
            String ruleVersion
    ) {
    }
}
