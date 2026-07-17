package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;

/**
 * Computes a single dimension of the cross-source V2 score.
 *
 * <p>Implementations are stateless Spring components. They receive a shared
 * {@link ScoringContext} so database lookups happen once per cluster scoring.
 */
public interface ScoreCalculator {

    /**
     * The component name, used as the key in {@code score_components}.
     *
     * @return stable component identifier (e.g. {@code "momentum"})
     */
    String name();

    /**
     * Computes the component score from the shared context.
     *
     * @param context the scoring context built for this cluster
     * @return the score component with 0..100 score, weight, and reasons
     */
    ScoreComponent compute(ScoringContext context);
}
