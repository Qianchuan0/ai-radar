package com.airadar.scoring.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.service.RuleBasedScoringService;
import org.springframework.stereotype.Component;

/**
 * V1 scoring strategy that transparently delegates to the existing
 * {@link RuleBasedScoringService}.
 *
 * <p>This wrapper exists so the {@link ScoringOrchestrator} can treat V1 and V2
 * uniformly through the {@link ClusterScoringStrategy} interface. The delegate's
 * behavior is unchanged: same version tag, same score components, same persistence.
 *
 * <p><b>Zero-behavior-change guarantee:</b> this class adds no logic beyond
 * delegation, so the V1 baseline replay test remains valid.
 */
@Component
public class HnScoreV1Strategy implements ClusterScoringStrategy {

    private final RuleBasedScoringService delegate;

    public HnScoreV1Strategy(RuleBasedScoringService delegate) {
        this.delegate = delegate;
    }

    @Override
    public String version() {
        return RuleBasedScoringService.SCORING_VERSION;
    }

    @Override
    public HotScoreEntity score(HotClusterEntity cluster) {
        return delegate.score(cluster);
    }
}
