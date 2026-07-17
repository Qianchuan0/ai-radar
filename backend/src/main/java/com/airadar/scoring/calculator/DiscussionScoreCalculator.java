package com.airadar.scoring.calculator;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the discussion dimension from normalized community discussion signals
 * (HackerNews comments, social engagement, etc.).
 *
 * <p>Mirrors {@link AdoptionScoreCalculator}: primary item discussion as the base
 * plus a cluster boost from other members.
 */
@Component
public class DiscussionScoreCalculator implements ScoreCalculator {

    public static final String NAME = "discussion";
    public static final double WEIGHT = 0.10;
    private static final double CLUSTER_BOOST_FACTOR = 0.2;

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
            return new ScoreComponent(NAME, 0.0, WEIGHT, reasons);
        }

        double primaryDiscussion = primarySignal.discussion();
        double clusterBoost = clusterDiscussionBoost(context);
        double score = clamp(primaryDiscussion + clusterBoost, 0.0, 100.0);

        reasons.add("primary_discussion=" + format(primaryDiscussion));
        reasons.add("cluster_boost=" + format(clusterBoost));
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }

    private double clusterDiscussionBoost(ScoringContext context) {
        HotItemEntity primary = context.primaryItem();
        if (primary == null) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (HotItemEntity item : context.activeItems()) {
            if (item.getId().equals(primary.getId())) {
                continue;
            }
            NormalizedSignal signal = context.signals().get(item.getId());
            if (signal != null) {
                sum += signal.discussion();
                count++;
            }
        }
        if (count == 0) {
            return 0.0;
        }
        return (sum / count) * CLUSTER_BOOST_FACTOR;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
