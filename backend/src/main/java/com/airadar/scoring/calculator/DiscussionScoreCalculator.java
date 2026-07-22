package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the discussion dimension from COMMUNITY source role signals
 * (HackerNews, Twitter, Weibo Hot Search).
 *
 * <p><b>Phase 18B refactor:</b> reads {@link SourceRole#COMMUNITY} signals
 * across the entire cluster rather than only the primary item. This avoids
 * undercounting discussion when a HN story and a Weibo hot search cover the
 * same event from different communities.
 */
@Component
public class DiscussionScoreCalculator implements ScoreCalculator {

    public static final String NAME = "discussion";
    public static final double WEIGHT = 0.10;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        List<String> reasons = new ArrayList<>();
        List<NormalizedSignal> communitySignals = context.signalsForRole(SourceRole.COMMUNITY);

        if (communitySignals.isEmpty()) {
            reasons.add("no_community_source");
            return new ScoreComponent(NAME, 0.0, WEIGHT, reasons);
        }

        double max = 0.0;
        double sum = 0.0;
        for (NormalizedSignal signal : communitySignals) {
            double value = signal.discussion();
            sum += value;
            if (value > max) {
                max = value;
            }
        }
        double average = sum / communitySignals.size();
        double score = clamp(max + (average * 0.15), 0.0, 100.0);

        reasons.add("community_sources=" + communitySignals.size());
        reasons.add("max_discussion=" + format(max));
        reasons.add("avg_discussion=" + format(average));
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
