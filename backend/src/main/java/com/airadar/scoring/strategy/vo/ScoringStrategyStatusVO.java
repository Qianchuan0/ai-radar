package com.airadar.scoring.strategy.vo;

/**
 * Phase 18B scoring strategy status payload.
 *
 * @param onlineVersion      the scoring version driving list / alert / report
 *                           ranking (e.g. {@code hn-score-v1} or
 *                           {@code cross-source-score-v2})
 * @param shadowVersion      the shadow scoring version always persisted
 *                           alongside the online version
 * @param v2Online           true when the online version is V2
 * @param rolloutStage       human-readable stage name; either
 *                           {@code V1_ONLINE_V2_SHADOW} or
 *                           {@code V2_ONLINE_V1_SHADOW}
 */
public record ScoringStrategyStatusVO(
        String onlineVersion,
        String shadowVersion,
        boolean v2Online,
        String rolloutStage
) {
}
