package com.airadar.evaluation.cluster;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate report emitted by {@link ClusterEvaluationService}.
 *
 * <p>The report carries both aggregate metrics (precision, recall,
 * false-merge, false-split) and the per-expectation breakdown so failures can
 * be triaged without rescanning the fixture.
 *
 * <p><b>Metric definitions</b> (Phase 16A baseline):
 * <ul>
 *   <li>{@code recall} = {@code mustMergeSatisfied / mustMergeTotal} — the
 *       fraction of must-merge groups the strategy placed in a single
 *       cluster. A miss here is a false split.</li>
 *   <li>{@code precision} = {@code mustNotMergeSatisfied / mustNotMergeTotal}
 *       — the fraction of must-not-merge pairs the strategy kept apart. A
 *       miss here is a false merge.</li>
 *   <li>{@code falseSplitCount} = must-merge groups that ended up split
 *       across multiple clusters</li>
 *   <li>{@code falseMergeCount} = must-not-merge pairs that ended up in the
 *       same cluster</li>
 * </ul>
 *
 * <p>When {@code mustMergeTotal == 0} recall is reported as {@link Double#NaN}.
 * The same applies to precision when {@code mustNotMergeTotal == 0}.
 */
public final class ClusterEvaluationReport {

    private final String strategyVersion;
    private final String fixtureVersion;
    private final Instant evaluatedAt;
    private final int totalItems;
    private final int totalClusters;
    private final int mustMergeTotal;
    private final int mustMergeSatisfied;
    private final int mustNotMergeTotal;
    private final int mustNotMergeSatisfied;
    private final double precision;
    private final double recall;
    private final int falseMergeCount;
    private final int falseSplitCount;
    private final List<ClusterEvaluationCaseResult> perCaseResults;
    private final Map<String, Long> itemToCluster;

    public ClusterEvaluationReport(
            String strategyVersion,
            String fixtureVersion,
            Instant evaluatedAt,
            int totalItems,
            int totalClusters,
            int mustMergeTotal,
            int mustMergeSatisfied,
            int mustNotMergeTotal,
            int mustNotMergeSatisfied,
            double precision,
            double recall,
            int falseMergeCount,
            int falseSplitCount,
            List<ClusterEvaluationCaseResult> perCaseResults,
            Map<String, Long> itemToCluster
    ) {
        this.strategyVersion = Objects.requireNonNull(strategyVersion, "strategyVersion");
        this.fixtureVersion = Objects.requireNonNull(fixtureVersion, "fixtureVersion");
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.totalItems = totalItems;
        this.totalClusters = totalClusters;
        this.mustMergeTotal = mustMergeTotal;
        this.mustMergeSatisfied = mustMergeSatisfied;
        this.mustNotMergeTotal = mustNotMergeTotal;
        this.mustNotMergeSatisfied = mustNotMergeSatisfied;
        this.precision = precision;
        this.recall = recall;
        this.falseMergeCount = falseMergeCount;
        this.falseSplitCount = falseSplitCount;
        this.perCaseResults = List.copyOf(Objects.requireNonNull(perCaseResults, "perCaseResults"));
        this.itemToCluster = new LinkedHashMap<>(Objects.requireNonNull(itemToCluster, "itemToCluster"));
    }

    public String getStrategyVersion() {
        return strategyVersion;
    }

    public String getFixtureVersion() {
        return fixtureVersion;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getTotalClusters() {
        return totalClusters;
    }

    public int getMustMergeTotal() {
        return mustMergeTotal;
    }

    public int getMustMergeSatisfied() {
        return mustMergeSatisfied;
    }

    public int getMustNotMergeTotal() {
        return mustNotMergeTotal;
    }

    public int getMustNotMergeSatisfied() {
        return mustNotMergeSatisfied;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public int getFalseMergeCount() {
        return falseMergeCount;
    }

    public int getFalseSplitCount() {
        return falseSplitCount;
    }

    public List<ClusterEvaluationCaseResult> getPerCaseResults() {
        return perCaseResults;
    }

    public Map<String, Long> getItemToCluster() {
        return itemToCluster;
    }
}
