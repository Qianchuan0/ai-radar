package com.airadar.scoring.strategy.model;

import java.util.List;

/**
 * A single dimension of a cross-source score.
 *
 * <p>Each component captures the raw 0-100 score, its weight in the total,
 * and human-readable reasons that explain how the score was derived. This
 * makes the V2 score fully explainable and replayable.
 *
 * @param name    component name (e.g. {@code "momentum"})
 * @param score   normalized score in the range 0..100
 * @param weight  contribution weight in the range 0..1 (sum of all weights = 1.0)
 * @param reasons human-readable explanations for traceability
 */
public record ScoreComponent(
        String name,
        double score,
        double weight,
        List<String> reasons
) {
    /**
     * Creates a component with no reasons.
     */
    public static ScoreComponent of(String name, double score, double weight) {
        return new ScoreComponent(name, score, weight, List.of());
    }

    /**
     * Returns this component's weighted contribution to the total score.
     *
     * @return {@code score * weight}
     */
    public double weightedContribution() {
        return score * weight;
    }
}
