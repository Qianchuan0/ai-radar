package com.airadar.cluster.strategy;

import java.util.Objects;

/**
 * One candidate cluster produced by {@code ClusterCandidateRetriever}.
 *
 * <p>Each candidate pairs a target cluster id (resolved from a matching
 * hot_item via {@code hot_cluster_item}) with the signal that caused
 * retrieval. The V2 matcher consults {@link #signals} to decide which layer
 * of match rules can fire.
 */
public final class CandidateCluster {

    private final Long hotItemId;
    private final Long clusterId;
    private final Signal signal;

    public CandidateCluster(Long hotItemId, Long clusterId, Signal signal) {
        this.hotItemId = Objects.requireNonNull(hotItemId, "hotItemId");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId");
        this.signal = Objects.requireNonNull(signal, "signal");
    }

    public Long getHotItemId() {
        return hotItemId;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public Signal getSignal() {
        return signal;
    }

    /**
     * Why this candidate was retrieved. The ordering reflects match-rule
     * priority: {@link #CANONICAL_URL} and {@link #EXTERNAL_ID} feed Level 1,
     * {@link #ENTITY} feeds Level 2, and {@link #KEYWORD} / {@link #TIME}
     * only contribute to Level 3 similarity.
     */
    public enum Signal {
        CANONICAL_URL,
        EXTERNAL_ID,
        ENTITY,
        KEYWORD,
        TIME
    }
}
