package com.airadar.scoring.calculator;

import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the authority dimension from the highest-authority source role
 * present in the cluster.
 *
 * <p>Authority is a proxy for credibility until citation/official-account signals
 * are wired in. Primary research artifacts (arXiv) score highest; developer
 * platforms (GitHub, HuggingFace) score moderately; community and discovery
 * sources score lowest.
 */
@Component
public class AuthorityScoreCalculator implements ScoreCalculator {

    public static final String NAME = "authority";
    public static final double WEIGHT = 0.10;
    private static final double FALLBACK_SCORE = 20.0;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        List<String> reasons = new ArrayList<>();

        SourceRole topRole = null;
        double topScore = FALLBACK_SCORE;

        for (NormalizedSignal signal : context.signals().values()) {
            SourceRole role = signal.sourceRole();
            double roleScore = scoreForRole(role);
            if (roleScore > topScore || topRole == null) {
                topRole = role;
                topScore = roleScore;
            }
        }

        if (topRole == null) {
            reasons.add("no_signals_in_cluster");
            return new ScoreComponent(NAME, FALLBACK_SCORE, WEIGHT, reasons);
        }

        reasons.add("top_role=" + topRole);
        reasons.add("score=" + format(topScore));
        return new ScoreComponent(NAME, topScore, WEIGHT, reasons);
    }

    private double scoreForRole(SourceRole role) {
        if (role == null) {
            return FALLBACK_SCORE;
        }
        return switch (role) {
            case PRIMARY -> 85.0;
            case ADOPTION -> 60.0;
            case MEDIA -> 55.0;
            case COMMUNITY -> 45.0;
            case DISCOVERY -> 25.0;
        };
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
