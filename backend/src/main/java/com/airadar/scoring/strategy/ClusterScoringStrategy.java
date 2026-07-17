package com.airadar.scoring.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.scoring.entity.HotScoreEntity;

/**
 * Strategy interface for scoring a hot cluster.
 *
 * <p>Each strategy implementation produces a versioned {@link HotScoreEntity}.
 * The entity is persisted by the strategy (or its delegate) so callers only need
 * to invoke {@link #score(HotClusterEntity)} within an appropriate transaction.
 *
 * <p>Phase 15 ships two strategies running side by side:
 * <ul>
 *   <li>{@code hn-score-v1} — the original rule-based baseline</li>
 *   <li>{@code cross-source-score-v2} — the semantic shadow score</li>
 * </ul>
 */
public interface ClusterScoringStrategy {

    /**
     * The scoring version tag, persisted as {@code hot_score.scoring_version}.
     *
     * @return stable version identifier (never {@code null})
     */
    String version();

    /**
     * Scores the cluster and persists the resulting {@link HotScoreEntity}.
     *
     * @param cluster the cluster to score
     * @return the persisted score entity
     */
    HotScoreEntity score(HotClusterEntity cluster);
}
