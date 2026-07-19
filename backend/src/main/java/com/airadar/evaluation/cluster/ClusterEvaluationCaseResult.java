package com.airadar.evaluation.cluster;

import java.util.List;
import java.util.Objects;

/**
 * Per-expectation outcome produced by {@link ClusterEvaluationService}.
 *
 * <p>Each case references either a must-merge group or a must-not-merge pair
 * from the fixture and reports whether the strategy satisfied it. The
 * {@link #clusterIds} list preserves the order of the input keys so callers
 * can see exactly how items were partitioned.
 */
public final class ClusterEvaluationCaseResult {

    private final ExpectationType expectationType;
    private final List<String> keys;
    private final boolean satisfied;
    private final List<Long> clusterIds;
    private final String detail;

    public ClusterEvaluationCaseResult(
            ExpectationType expectationType,
            List<String> keys,
            boolean satisfied,
            List<Long> clusterIds,
            String detail
    ) {
        this.expectationType = Objects.requireNonNull(expectationType, "expectationType");
        this.keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        this.satisfied = satisfied;
        this.clusterIds = List.copyOf(Objects.requireNonNull(clusterIds, "clusterIds"));
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public ExpectationType getExpectationType() {
        return expectationType;
    }

    public List<String> getKeys() {
        return keys;
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public List<Long> getClusterIds() {
        return clusterIds;
    }

    public String getDetail() {
        return detail;
    }

    public enum ExpectationType {
        MUST_MERGE,
        MUST_NOT_MERGE
    }
}
