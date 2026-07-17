package com.airadar.scoring.calculator;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the freshness dimension from the primary item's publication time.
 *
 * <p>Uses the same 72-hour decay window as the V1 {@code RuleBasedScoringService}
 * so the two scores remain comparable. A freshly published item scores 100; an
 * item older than 72 hours scores 0.
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
        HotItemEntity primary = context.primaryItem();

        if (primary == null) {
            reasons.add("no_primary_item");
            return new ScoreComponent(NAME, 0.0, WEIGHT, reasons);
        }

        Instant publishedAt = primary.getPublishedAt();
        if (publishedAt == null) {
            reasons.add("primary_item=" + primary.getId() + ",published_at_missing");
            return new ScoreComponent(NAME, 0.0, WEIGHT, reasons);
        }

        long ageHours = Math.max(0L, Duration.between(publishedAt, context.now()).toHours());
        double score = Math.max(0.0, 100.0 * (1.0 - (double) ageHours / FRESH_WINDOW_HOURS));

        reasons.add("primary_item=" + primary.getId());
        reasons.add("age_hours=" + ageHours);
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }
}
