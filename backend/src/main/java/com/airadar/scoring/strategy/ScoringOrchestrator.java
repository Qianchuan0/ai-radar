package com.airadar.scoring.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.scoring.entity.HotScoreEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates V1 (online) and V2 (shadow) scoring for a single cluster.
 *
 * <p>V1 runs on the orchestrator's transaction and remains the source of truth
 * for the live ranking. V2 runs in its own {@code REQUIRES_NEW} transaction; any
 * failure is logged and swallowed so the crawl pipeline is never blocked by a
 * shadow-scoring bug.
 */
@Service
public class ScoringOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ScoringOrchestrator.class);

    private final HnScoreV1Strategy v1Strategy;
    private final CrossSourceScoreV2Strategy v2Strategy;

    public ScoringOrchestrator(HnScoreV1Strategy v1Strategy, CrossSourceScoreV2Strategy v2Strategy) {
        this.v1Strategy = v1Strategy;
        this.v2Strategy = v2Strategy;
    }

    /**
     * Scores the cluster with V1 (authoritative) and V2 (shadow).
     *
     * <p>V1 exceptions propagate to the caller so the pipeline's existing
     * {@code CrawlStage.SCORE} error handling stays unchanged. V2 exceptions are
     * captured here so they never affect the online score.
     *
     * @param cluster the cluster to score
     * @return the persisted V1 score entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HotScoreEntity run(HotClusterEntity cluster) {
        HotScoreEntity v1Entity = v1Strategy.score(cluster);

        try {
            v2Strategy.score(cluster);
        } catch (RuntimeException ex) {
            log.warn("V2 shadow scoring failed for cluster {}: {}",
                    cluster.getId(), ex.toString());
        }
        return v1Entity;
    }
}
