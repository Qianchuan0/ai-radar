package com.airadar.evaluation.realtimedata;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate report emitted by
 * {@link RealDataClusterEvaluationService#evaluate(long, String, Instant)}.
 *
 * <p><b>Metric definitions</b> (Phase 17A real-data baseline):
 * <ul>
 *   <li>{@code pairwisePrecision} = {@code TP / (TP + FP)}: fraction of
 *       must-not-merge pairs the strategy kept apart that it actually kept
 *       apart. Misses here are false merges.</li>
 *   <li>{@code pairwiseRecall} = {@code TP / (TP + FN)}: fraction of
 *       must-merge groups the strategy put together. Misses are false
 *       splits.</li>
 *   <li>{@code pairwiseF1} = harmonic mean of precision and recall.</li>
 *   <li>{@code falseMergeRate} = {@code FP / (FP + TN)}: false merges
 *       relative to all must-not-merge pairs.</li>
 *   <li>{@code falseSplitRate} = {@code FN / (TP + FN)}: false splits
 *       relative to all must-merge pairs.</li>
 *   <li>{@code reviewRequiredRate}: V2-only. Fraction of evaluated items
 *       that produced at least one {@code REVIEW_REQUIRED} decision in
 *       {@code cluster_match_decision}. For V1 this is always 0.</li>
 * </ul>
 *
 * <p>Metrics are reported as {@link Double#NaN} when the denominator is 0.
 */
public final class ClusterPairEvaluationReport {

    private final String strategyVersion;
    private final Instant evaluatedAt;
    private final int totalPairs;
    private final int truePositives;
    private final int falsePositives;
    private final int trueNegatives;
    private final int falseNegatives;
    private final int ambiguousCount;
    private final int unresolvedCount;
    private final int reviewRequiredItems;
    private final double pairwisePrecision;
    private final double pairwiseRecall;
    private final double pairwiseF1;
    private final double falseMergeRate;
    private final double falseSplitRate;
    private final double reviewRequiredRate;
    private final Map<String, CategorySlice> slicesByCategory;
    private final List<PairCaseResult> perCaseResults;

    public ClusterPairEvaluationReport(
            String strategyVersion,
            Instant evaluatedAt,
            int totalPairs,
            int truePositives,
            int falsePositives,
            int trueNegatives,
            int falseNegatives,
            int ambiguousCount,
            int unresolvedCount,
            int reviewRequiredItems,
            double pairwisePrecision,
            double pairwiseRecall,
            double pairwiseF1,
            double falseMergeRate,
            double falseSplitRate,
            double reviewRequiredRate,
            Map<String, CategorySlice> slicesByCategory,
            List<PairCaseResult> perCaseResults
    ) {
        this.strategyVersion = Objects.requireNonNull(strategyVersion, "strategyVersion");
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.totalPairs = totalPairs;
        this.truePositives = truePositives;
        this.falsePositives = falsePositives;
        this.trueNegatives = trueNegatives;
        this.falseNegatives = falseNegatives;
        this.ambiguousCount = ambiguousCount;
        this.unresolvedCount = unresolvedCount;
        this.reviewRequiredItems = reviewRequiredItems;
        this.pairwisePrecision = pairwisePrecision;
        this.pairwiseRecall = pairwiseRecall;
        this.pairwiseF1 = pairwiseF1;
        this.falseMergeRate = falseMergeRate;
        this.falseSplitRate = falseSplitRate;
        this.reviewRequiredRate = reviewRequiredRate;
        this.slicesByCategory = Map.copyOf(Objects.requireNonNull(slicesByCategory, "slicesByCategory"));
        this.perCaseResults = List.copyOf(Objects.requireNonNull(perCaseResults, "perCaseResults"));
    }

    public String getStrategyVersion() {
        return strategyVersion;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public int getTotalPairs() {
        return totalPairs;
    }

    public int getTruePositives() {
        return truePositives;
    }

    public int getFalsePositives() {
        return falsePositives;
    }

    public int getTrueNegatives() {
        return trueNegatives;
    }

    public int getFalseNegatives() {
        return falseNegatives;
    }

    public int getAmbiguousCount() {
        return ambiguousCount;
    }

    public int getUnresolvedCount() {
        return unresolvedCount;
    }

    public int getReviewRequiredItems() {
        return reviewRequiredItems;
    }

    public double getPairwisePrecision() {
        return pairwisePrecision;
    }

    public double getPairwiseRecall() {
        return pairwiseRecall;
    }

    public double getPairwiseF1() {
        return pairwiseF1;
    }

    public double getFalseMergeRate() {
        return falseMergeRate;
    }

    public double getFalseSplitRate() {
        return falseSplitRate;
    }

    public double getReviewRequiredRate() {
        return reviewRequiredRate;
    }

    public Map<String, CategorySlice> getSlicesByCategory() {
        return slicesByCategory;
    }

    public List<PairCaseResult> getPerCaseResults() {
        return perCaseResults;
    }

    /**
     * Per-category rollup so failures can be triaged by event kind (model
     * release vs security incident vs same-name-different-event etc).
     */
    public static final class CategorySlice {
        private final String category;
        private final int totalPairs;
        private final int truePositives;
        private final int falsePositives;
        private final int trueNegatives;
        private final int falseNegatives;
        private final double precision;
        private final double recall;
        private final double f1;

        public CategorySlice(
                String category,
                int totalPairs,
                int truePositives,
                int falsePositives,
                int trueNegatives,
                int falseNegatives,
                double precision,
                double recall,
                double f1
        ) {
            this.category = Objects.requireNonNull(category, "category");
            this.totalPairs = totalPairs;
            this.truePositives = truePositives;
            this.falsePositives = falsePositives;
            this.trueNegatives = trueNegatives;
            this.falseNegatives = falseNegatives;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
        }

        public String getCategory() {
            return category;
        }

        public int getTotalPairs() {
            return totalPairs;
        }

        public int getTruePositives() {
            return truePositives;
        }

        public int getFalsePositives() {
            return falsePositives;
        }

        public int getTrueNegatives() {
            return trueNegatives;
        }

        public int getFalseNegatives() {
            return falseNegatives;
        }

        public double getPrecision() {
            return precision;
        }

        public double getRecall() {
            return recall;
        }

        public double getF1() {
            return f1;
        }
    }

    /**
     * Per-pair outcome so failures can be triaged without rescanning the
     * source file.
     */
    public static final class PairCaseResult {
        private final String caseCode;
        private final String expectation;
        private final String category;
        private final Long itemA;
        private final Long itemB;
        private final Long clusterA;
        private final Long clusterB;
        private final String outcome;  // TP / FP / TN / FN / SKIP / UNRESOLVED
        private final String reason;

        public PairCaseResult(
                String caseCode,
                String expectation,
                String category,
                Long itemA,
                Long itemB,
                Long clusterA,
                Long clusterB,
                String outcome,
                String reason
        ) {
            this.caseCode = Objects.requireNonNull(caseCode, "caseCode");
            this.expectation = expectation;
            this.category = category;
            this.itemA = itemA;
            this.itemB = itemB;
            this.clusterA = clusterA;
            this.clusterB = clusterB;
            this.outcome = outcome;
            this.reason = reason;
        }

        public String getCaseCode() {
            return caseCode;
        }

        public String getExpectation() {
            return expectation;
        }

        public String getCategory() {
            return category;
        }

        public Long getItemA() {
            return itemA;
        }

        public Long getItemB() {
            return itemB;
        }

        public Long getClusterA() {
            return clusterA;
        }

        public Long getClusterB() {
            return clusterB;
        }

        public String getOutcome() {
            return outcome;
        }

        public String getReason() {
            return reason;
        }
    }
}
