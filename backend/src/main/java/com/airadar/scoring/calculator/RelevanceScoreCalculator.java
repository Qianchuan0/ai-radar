package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the relevance dimension from normalized relevance signals.
 *
 * <p>Search sources (Bing, DuckDuckGo, Sogou) carry explicit relevance derived
 * from ranking position. Non-search sources have a relevance of 0 in their
 * normalized signal, so this calculator falls back to a neutral baseline to
 * avoid penalizing topics that simply were not discovered via search.
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
        NormalizedSignal primarySignal = context.primarySignal();

        if (primarySignal == null) {
            reasons.add("no_signal_for_primary_item");
            return new ScoreComponent(NAME, NEUTRAL_BASELINE, WEIGHT, reasons);
        }

        double relevance = primarySignal.relevance();
        if (primarySignal.isSearchResult() || relevance > 0.0) {
            reasons.add("primary_relevance=" + format(relevance));
            reasons.add("search_rank=" + primarySignal.getRank().map(String::valueOf).orElse("n/a"));
            return new ScoreComponent(NAME, clamp(relevance, 0.0, 100.0), WEIGHT, reasons);
        }

        reasons.add("non_search_source,fallback=" + format(NEUTRAL_BASELINE));
        return new ScoreComponent(NAME, NEUTRAL_BASELINE, WEIGHT, reasons);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
