package com.airadar.scoring.calculator;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the adoption dimension from normalized adoption signals
 * (GitHub stars, HuggingFace downloads, etc.).
 *
 * <p>Uses the primary item's adoption as the base, with a small cluster boost
 * from other members' average adoption. This rewards clusters that show broad
 * adoption across multiple items without letting a single low-adoption member
 * drag the score down.
 */
@Component
public class AdoptionScoreCalculator implements ScoreCalculator {

    public static final String NAME = "adoption";
    public static final double WEIGHT = 0.15;
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

        double primaryAdoption = primarySignal.adoption();
        double clusterBoost = clusterAdoptionBoost(context);
        double score = clamp(primaryAdoption + clusterBoost, 0.0, 100.0);

        reasons.add("primary_adoption=" + format(primaryAdoption));
        reasons.add("cluster_boost=" + format(clusterBoost));
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }

    private double clusterAdoptionBoost(ScoringContext context) {
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
                sum += signal.adoption();
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
