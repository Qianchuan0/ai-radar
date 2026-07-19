package com.airadar.cluster.strategy.vo;

import java.util.List;

/**
 * Snapshot of the live cluster strategy configuration.
 *
 * <p>Returned by {@code GET /api/v1/cluster-strategy/status}. Designed for
 * internal debugging during the Phase 17C gradual rollout so operators
 * can confirm which path the crawl pipeline is currently taking.
 */
public record ClusterStrategyStatusVO(
        String onlineStrategy,
        String shadowStrategy,
        boolean shadowEnabled,
        boolean v2OnlineEnabled,
        int trafficPercent,
        List<String> allowedMatchLevels,
        double l3MinScore,
        boolean reviewRequiredToQueue,
        List<String> sourceAllowlist,
        String rolloutStage
) {
}
