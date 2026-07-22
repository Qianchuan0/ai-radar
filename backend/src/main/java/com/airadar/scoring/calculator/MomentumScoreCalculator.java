package com.airadar.scoring.calculator;

import com.airadar.cluster.model.ClusterTrend;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.GrowthConfidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the momentum dimension from the cluster-level 24h trend.
 *
 * <p>Momentum is the highest-weighted V2 dimension (0.25) because it captures
 * current growth velocity rather than cumulative scale. A brand-new project with
 * 600 stars/day should outscore a 100k-star project that is flat.
 *
 * <p><b>Phase 18B refactor:</b> reads {@link ClusterTrend#momentumScore()}
 * (aggregated across all active items with discovery-source dedup) instead of
 * {@code context.primaryGrowth()}. Confidence attenuation still applies so
 * unreliable trend data cannot inflate the score.
 */
@Component
public class MomentumScoreCalculator implements ScoreCalculator {

    public static final String NAME = "momentum";
    public static final double WEIGHT = 0.25;
    private static final double NO_TREND_BASELINE = 10.0;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        List<String> reasons = new ArrayList<>();
        ClusterTrend trend = context.clusterTrend();

        if (trend == null || trend.momentumScore() == null) {
            reasons.add(trend == null ? "no_cluster_trend" : "momentum_not_computed");
            return new ScoreComponent(NAME, NO_TREND_BASELINE, WEIGHT, reasons);
        }

        double raw = clamp(trend.momentumScore(), 0.0, 100.0);
        GrowthConfidence confidence = trend.confidence();
        double confidenceFactor = confidenceFactor(confidence);
        double score = clamp(raw * confidenceFactor, 0.0, 100.0);

        reasons.add("window=" + trend.window());
        reasons.add("contributing_items=" + (trend.contributingItems() == null ? 0 : trend.contributingItems().size()));
        reasons.add("raw_momentum=" + format(raw));
        reasons.add("confidence=" + confidence + "*factor=" + format(confidenceFactor));
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
