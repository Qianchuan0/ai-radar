package com.airadar.cluster.strategy;

import java.util.List;

/**
 * Result of {@link EventRuleClusterStrategy#evaluateForOnline} — the full V2
 * pipeline (extract features, retrieve candidates, layered match, persist
 * decisions) paired with the best candidate outcome, without touching any
 * {@code hot_cluster_item} row.
 *
 * <p>The Phase 17C orchestrator uses this to decide whether to move the
 * item from its V1 singleton into the V2 target cluster.
 *
 * @param decisions every persisted {@code cluster_match_decision} row;
 *                  empty only when the candidate set was empty
 * @param bestOutcome the highest-ranked outcome across all candidates;
 *                    {@code null} when no candidate had a feature row
 * @param bestCandidate the candidate that produced {@code bestOutcome};
 *                      {@code null} alongside a {@code null} best outcome
 */
public record V2OnlineEvaluation(
        List<ClusterMatchDecisionEntity> decisions,
        MatchOutcome bestOutcome,
        CandidateCluster bestCandidate
) {
}
