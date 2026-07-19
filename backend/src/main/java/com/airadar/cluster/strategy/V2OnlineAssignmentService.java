package com.airadar.cluster.strategy;

import com.airadar.cluster.governance.ClusterReviewService;
import com.airadar.cluster.governance.MembershipAction;
import com.airadar.cluster.governance.MoveItemService;
import com.airadar.cluster.governance.OperatorType;
import com.airadar.cluster.governance.vo.MoveItemResultVO;
import com.airadar.item.entity.HotItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Phase 17C V2 online assignment step.
 *
 * <p>Runs V2 evaluate (persisting every candidate decision), picks the best
 * outcome, applies the level / confidence / target gates, and — only when
 * every gate clears — relocates the item from its V1 singleton into the V2
 * target cluster through {@link MoveItemService}. Every successful move
 * writes a {@code cluster_membership_history} row via the governance layer,
 * so V2 online writes are auditable through the same path as manual ops.
 *
 * <p>The whole step runs inside a {@link Propagation#NESTED} transaction so
 * a V2-side failure (SQL constraint violation, governance guard rejection,
 * candidate cluster vanished) only rolls back to the savepoint. V1's
 * already-created singleton cluster stays committed with the outer
 * transaction.
 *
 * <p>{@link #apply(HotItemEntity, long)} assumes the caller has already
 * checked the configuration / traffic / source gates via
 * {@link ClusterStrategyProperties}. The service still re-checks the
 * per-outcome gates (allowed layer, L3 min-score, self-match, target ==
 * source) because they depend on the V2 result.
 */
@Service
public class V2OnlineAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(V2OnlineAssignmentService.class);

    private final EventRuleClusterStrategy v2Strategy;
    private final MoveItemService moveItemService;
    private final ClusterReviewService reviewService;
    private final ClusterStrategyProperties properties;

    public V2OnlineAssignmentService(
            EventRuleClusterStrategy v2Strategy,
            MoveItemService moveItemService,
            ClusterReviewService reviewService,
            ClusterStrategyProperties properties
    ) {
        this.v2Strategy = v2Strategy;
        this.moveItemService = moveItemService;
        this.reviewService = reviewService;
        this.properties = properties;
    }

    /**
     * Runs the V2 online attempt for a single item whose V1 assignment has
     * already produced the {@code v1ClusterId} singleton.
     *
     * @return a {@link V2OnlineResult} that is either {@code moved} (the
     *         item was relocated) or {@code skipped} (no move happened for
     *         the documented reason). Never throws — V2-side errors bubble
     *         out only when the caller's transaction boundary hits them,
     *         which is what triggers the savepoint rollback.
     * @param item      the hot item (must already be persisted with an
     *                  active V1 membership)
     * @param v1ClusterId the cluster the V1 strategy just placed the item in
     */
    @Transactional(propagation = Propagation.NESTED)
    public V2OnlineResult apply(HotItemEntity item, long v1ClusterId) {
        V2OnlineEvaluation evaluation = v2Strategy.evaluateForOnline(item);

        // REVIEW_REQUIRED decisions should surface in the queue immediately
        // when the operator opted into proactive materialization. This is
        // safe to call repeatedly (unique index on cluster_match_decision_id).
        ClusterStrategyProperties.V2Online config = properties.getV2Online();
        if (config.isReviewRequiredToQueue()) {
            try {
                reviewService.materializeOpenTasks();
            } catch (RuntimeException ex) {
                log.warn("V2 online: review task materialization failed for item {}: {}",
                        item.getId(), ex.toString());
            }
        }

        if (evaluation.bestOutcome() == null || evaluation.bestCandidate() == null) {
            return V2OnlineResult.skipped(evaluation.decisions(), "NO_BEST_CANDIDATE");
        }

        MatchOutcome best = evaluation.bestOutcome();
        if (best.getDecision() != AssignmentDecision.ACCEPTED) {
            return V2OnlineResult.skipped(evaluation.decisions(),
                    "BEST_DECISION_" + best.getDecision().name());
        }

        Set<String> allowedLevels = config.allowedLevelSet();
        if (!allowedLevels.contains(best.getLayer())) {
            return V2OnlineResult.skipped(evaluation.decisions(),
                    "LAYER_" + best.getLayer() + "_NOT_ALLOWED");
        }

        if (!passesConfidenceGate(best, config)) {
            return V2OnlineResult.skipped(evaluation.decisions(),
                    "L3_SCORE_BELOW_" + config.getL3MinScore());
        }

        Long targetClusterId = evaluation.bestCandidate().getClusterId();
        if (targetClusterId == null || targetClusterId == v1ClusterId) {
            return V2OnlineResult.skipped(evaluation.decisions(), "TARGET_EQUALS_SOURCE");
        }

        Long candidateItemId = evaluation.bestCandidate().getHotItemId();
        if (candidateItemId != null && candidateItemId.equals(item.getId())) {
            return V2OnlineResult.skipped(evaluation.decisions(), "SELF_MATCH");
        }

        MoveItemResultVO moveResult = moveItemService.move(
                v1ClusterId,
                item.getId(),
                targetClusterId,
                buildReason(best),
                OperatorType.SYSTEM.name()
        );
        return V2OnlineResult.moved(evaluation.decisions(),
                targetClusterId, moveResult.historyId());
    }

    private boolean passesConfidenceGate(MatchOutcome outcome,
                                         ClusterStrategyProperties.V2Online config) {
        if (!"L3".equals(outcome.getLayer())) {
            return true;
        }
        BigDecimal threshold = BigDecimal.valueOf(config.getL3MinScore());
        return outcome.getScore() != null && outcome.getScore().compareTo(threshold) >= 0;
    }

    private String buildReason(MatchOutcome outcome) {
        return "V2 online auto-merge via " + MembershipAction.MOVE.name()
                + ": layer=" + outcome.getLayer()
                + ", method=" + outcome.getMethod()
                + ", score=" + outcome.getScore();
    }
}
