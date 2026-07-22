package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the freshness dimension from the earliest credible event time.
 *
 * <p>Uses the same 72-hour decay window as the V1 {@code RuleBasedScoringService}
 * so the two scores remain comparable. A freshly observed event scores 100; an
 * event older than 72 hours scores 0.
 *
 * <p><b>Phase 18B refactor:</b> the reference time is now
 * {@code context.earliestCredibleEventAt()}, computed by
 * {@code CrossSourceScoreV2Strategy} as the earliest {@code published_at} among
 * non-DISCOVERY items (i.e. primary / adoption / community evidence) with
 * fallback to {@code cluster.firstSeenAt}. Discovery-only items no longer
 * determine freshness because they reflect search-engine indexing time rather
 * than the underlying event time.
 */
@Component
public class FreshnessScoreCalculator implements ScoreCalculator {

    public static final String NAME = "freshness";
    public static final double WEIGHT = 0.15;
    private static final long FRESH_WINDOW_HOURS = 72L;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        List<String> reasons = new ArrayList<>();
        Instant reference = context.earliestCredibleEventAt();

        if (reference == null) {
            reasons.add("no_credible_event_time");
            return new ScoreComponent(NAME, 0.0, WEIGHT, reasons);
        }

        long ageHours = Math.max(0L, Duration.between(reference, context.now()).toHours());
        double score = Math.max(0.0, 100.0 * (1.0 - (double) ageHours / FRESH_WINDOW_HOURS));

        reasons.add("event_time=" + reference);
        reasons.add("age_hours=" + ageHours);
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }
}
