package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the relevance dimension from DISCOVERY source role signals
 * (Bing, DuckDuckGo, Sogou search).
 *
 * <p>Search sources carry explicit relevance derived from ranking position.
 * Non-search sources have a relevance of 0 in their normalized signal, so this
 * calculator falls back to a neutral baseline when the cluster has no
 * DISCOVERY evidence at all.
 *
 * <p><b>Phase 18B refactor:</b> aggregates relevance across every DISCOVERY
 * item in the cluster, not only the primary item. The cluster-level relevance
 * is the maximum (best rank) plus a small boost from the average so multiple
 * search engines agreeing on the same URL lift relevance slightly.
 */
@Component
public class RelevanceScoreCalculator implements ScoreCalculator {

    public static final String NAME = "relevance";
    public static final double WEIGHT = 0.15;
    private static final double NEUTRAL_BASELINE = 50.0;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        List<String> reasons = new ArrayList<>();
        List<NormalizedSignal> discoverySignals = context.signalsForRole(SourceRole.DISCOVERY);

        if (discoverySignals.isEmpty()) {
            reasons.add("no_discovery_source,fallback=" + format(NEUTRAL_BASELINE));
            return new ScoreComponent(NAME, NEUTRAL_BASELINE, WEIGHT, reasons);
        }

        double max = 0.0;
        double sum = 0.0;
        int maxRank = Integer.MAX_VALUE;
        for (NormalizedSignal signal : discoverySignals) {
            double value = signal.relevance();
            sum += value;
            if (value > max) {
                max = value;
            }
            if (signal.getRank().isPresent()) {
                int rank = signal.getRank().get();
                if (rank < maxRank) {
                    maxRank = rank;
                }
            }
        }
        double average = sum / discoverySignals.size();
        double score = clamp(max + (average * 0.10), 0.0, 100.0);

        reasons.add("discovery_sources=" + discoverySignals.size());
        reasons.add("max_relevance=" + format(max));
        reasons.add("avg_relevance=" + format(average));
        reasons.add("best_rank=" + (maxRank == Integer.MAX_VALUE ? "n/a" : maxRank));
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
