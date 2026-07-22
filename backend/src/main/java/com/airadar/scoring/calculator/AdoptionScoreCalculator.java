package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

/**
 * Computes the adoption dimension from ADOPTION source role signals
 * (GitHub stars, HuggingFace downloads, etc.).
 *
 * <p><b>Phase 18B refactor:</b> reads {@link SourceRole#ADOPTION} signals
 * across the entire cluster rather than only the primary item. This ensures
 * events with multiple adoption signals (e.g. a GitHub repo + a Hugging Face
 * model) are not undercounted when neither happens to be the primary item.
 */
@Component
public class AdoptionScoreCalculator implements ScoreCalculator {

    public static final String NAME = "adoption";
    public static final double WEIGHT = 0.15;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        List<String> reasons = new ArrayList<>();
        List<NormalizedSignal> adoptionSignals = context.signalsForRole(SourceRole.ADOPTION);

        if (adoptionSignals.isEmpty()) {
            reasons.add("no_adoption_source");
            return new ScoreComponent(NAME, 0.0, WEIGHT, reasons);
        }

        double max = 0.0;
        double sum = 0.0;
        for (NormalizedSignal signal : adoptionSignals) {
            double value = signal.adoption();
            sum += value;
            if (value > max) {
                max = value;
            }
        }
        // Use max-of-cluster as the base (one strong adopter is enough to mark the
        // event as significant) and add a small boost from the average of the rest
        // so a cluster with multiple adoption signals still ranks higher.
        double average = sum / adoptionSignals.size();
        double score = clamp(max + (average * 0.15), 0.0, 100.0);

        reasons.add("adoption_sources=" + adoptionSignals.size());
        reasons.add("max_adoption=" + format(max));
        reasons.add("avg_adoption=" + format(average));
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
