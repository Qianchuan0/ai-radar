package com.airadar.scoring.strategy.model;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated score breakdown persisted in {@code hot_score.score_components}.
 *
 * <p>This record is serialized to JSON for storage and API responses. It captures
 * every dimension so the V2 score can be fully explained and replayed.
 *
 * @param version      scoring version (e.g. {@code "cross-source-score-v2"})
 * @param total        weighted total score in the range 0..100
 * @param components   ordered list of score dimensions
 * @param calculatedAt calculation timestamp
 */
public record ScoreComponents(
        String version,
        double total,
        List<ScoreComponent> components,
        Instant calculatedAt
) {
}
