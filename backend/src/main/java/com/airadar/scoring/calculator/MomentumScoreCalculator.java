package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.GrowthMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the momentum dimension from the primary item's 24h growth trend.
 *
 * <p>Momentum is the highest-weighted V2 dimension (0.25) because it captures
 * current growth velocity rather than cumulative scale. A brand-new project with
 * 600 stars/day should outscore a 100k-star project that is flat.
 *
 * <p>Confidence attenuation prevents unreliable growth data from inflating the
 * score: when no historical snapshot exists or metrics look anomalous, the
 * momentum contribution is heavily discounted.
 */
@Component
public class MomentumScoreCalculator implements ScoreCalculator {

    public static final String NAME = "momentum";
    public static final double WEIGHT = 0.25;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        GrowthMetrics growth = context.primaryGrowth();
        List<String> reasons = new ArrayList<>();

        if (growth == null) {
            reasons.add("no_growth_data_for_primary_item");
            return new ScoreComponent(NAME, 10.0, WEIGHT, reasons);
        }

        Double rawMomentum = growth.momentumScore();
        if (rawMomentum == null) {
            reasons.add("momentum_not_computed");
            return new ScoreComponent(NAME, 10.0, WEIGHT, reasons);
        }

        double clamped = clamp(rawMomentum, 0.0, 100.0);
        double confidenceFactor = confidenceFactor(growth.confidence());
        double score = clamp(clamped * confidenceFactor, 0.0, 100.0);

        reasons.add("primary_item=" + growth.hotItemId());
        reasons.add("raw_momentum=" + format(rawMomentum));
        reasons.add("confidence=" + growth.confidence() + "*factor=" + format(confidenceFactor));
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }

    private double confidenceFactor(GrowthConfidence confidence) {
        if (confidence == null) {
            return 0.3;
        }
        return switch (confidence) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.85;
            case LOW -> 0.6;
            case UNKNOWN, DATA_ANOMALY, METRIC_RESET -> 0.3;
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
