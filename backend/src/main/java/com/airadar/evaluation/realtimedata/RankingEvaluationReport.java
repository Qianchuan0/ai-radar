package com.airadar.evaluation.realtimedata;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate ranking-quality report emitted by
 * {@link RankingEvaluationService#evaluate(long, String, int, Instant)}.
 *
 * <p><b>Metric definitions</b> (Phase 17A real-data baseline):
 * <ul>
 *   <li>{@code precisionAtN} = fraction of the top-N ranked clusters whose
 *       labeled relevance is {@code RELEVANT} or {@code HIGHLY_RELEVANT}.
 *       Unlabeled clusters in the top-N count as not relevant.</li>
 *   <li>{@code ndcgAtN} = {@code DCG@N / IDCG@N}. Gain mapping:
 *       {@code HIGHLY_RELEVANT=3, RELEVANT=2, MARGINALLY_RELEVANT=1,
 *       NOISE=0, unlabeled=0}. Discount is {@code 1 / log2(rank + 1)}.
 *       {@code IDCG@N} is computed over all labeled clusters in the
 *       window, sorted by gain desc. {@code NaN} when {@code IDCG@N == 0}.
 *   <li>{@code topNNoiseRate} = fraction of the top-N explicitly labeled
 *       {@code NOISE}. Unlabeled clusters in the top-N are not counted as
 *       noise.</li>
 *   <li>{@code majorEventMissRate} = {@code majorEventMissed / majorEventTotal}
 *       — fraction of clusters labeled {@code isMajorEvent=true} that did not
 *       land in the top-N.</li>
 *   <li>{@code rankingDiffVsV1} (V2 only): symmetric-difference size between
 *       V1 top-N and V2 top-N cluster id sets. {@code null} for V1 reports.</li>
 * </ul>
 *
 * <p>Per-window slices are reported in {@link #getWindowReports()}.
 */
public final class RankingEvaluationReport {

    private final String scoringVersion;
    private final int topN;
    private final Instant evaluatedAt;
    private final int windowCount;
    private final int labeledClusterCount;
    private final int missingScoreCount;
    private final double precisionAtN;
    private final double ndcgAtN;
    private final double topNNoiseRate;
    private final int majorEventTotal;
    private final int majorEventMissed;
    private final double majorEventMissRate;
    private final Integer rankingDiffVsV1TopN;
    private final List<WindowReport> windowReports;

    public RankingEvaluationReport(
            String scoringVersion,
            int topN,
            Instant evaluatedAt,
            int windowCount,
            int labeledClusterCount,
            int missingScoreCount,
            double precisionAtN,
            double ndcgAtN,
            double topNNoiseRate,
            int majorEventTotal,
            int majorEventMissed,
            double majorEventMissRate,
            Integer rankingDiffVsV1TopN,
            List<WindowReport> windowReports
    ) {
        this.scoringVersion = Objects.requireNonNull(scoringVersion, "scoringVersion");
        this.topN = topN;
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.windowCount = windowCount;
        this.labeledClusterCount = labeledClusterCount;
        this.missingScoreCount = missingScoreCount;
        this.precisionAtN = precisionAtN;
        this.ndcgAtN = ndcgAtN;
        this.topNNoiseRate = topNNoiseRate;
        this.majorEventTotal = majorEventTotal;
        this.majorEventMissed = majorEventMissed;
        this.majorEventMissRate = majorEventMissRate;
        this.rankingDiffVsV1TopN = rankingDiffVsV1TopN;
        this.windowReports = List.copyOf(Objects.requireNonNull(windowReports, "windowReports"));
    }

    public String getScoringVersion() {
        return scoringVersion;
    }

    public int getTopN() {
        return topN;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public int getWindowCount() {
        return windowCount;
    }

    public int getLabeledClusterCount() {
        return labeledClusterCount;
    }

    public int getMissingScoreCount() {
        return missingScoreCount;
    }

    public double getPrecisionAtN() {
        return precisionAtN;
    }

    public double getNdcgAtN() {
        return ndcgAtN;
    }

    public double getTopNNoiseRate() {
        return topNNoiseRate;
    }

    public int getMajorEventTotal() {
        return majorEventTotal;
    }

    public int getMajorEventMissed() {
        return majorEventMissed;
    }

    public double getMajorEventMissRate() {
        return majorEventMissRate;
    }

    public Integer getRankingDiffVsV1TopN() {
        return rankingDiffVsV1TopN;
    }

    public List<WindowReport> getWindowReports() {
        return windowReports;
    }

    /**
     * Per-window slice. One row per distinct {@code windowStart} seen in the
     * labeled dataset.
     */
    public static final class WindowReport {
        private final String windowStart;
        private final String windowEnd;
        private final int labeledClusters;
        private final int rankableClusters;
        private final int missingScores;
        private final double precisionAtN;
        private final double ndcgAtN;
        private final double topNNoiseRate;
        private final int majorEventTotal;
        private final int majorEventMissed;
        private final List<RankedCluster> topNClusters;

        public WindowReport(
                String windowStart,
                String windowEnd,
                int labeledClusters,
                int rankableClusters,
                int missingScores,
                double precisionAtN,
                double ndcgAtN,
                double topNNoiseRate,
                int majorEventTotal,
                int majorEventMissed,
                List<RankedCluster> topNClusters
        ) {
            this.windowStart = Objects.requireNonNull(windowStart, "windowStart");
            this.windowEnd = windowEnd;
            this.labeledClusters = labeledClusters;
            this.rankableClusters = rankableClusters;
            this.missingScores = missingScores;
            this.precisionAtN = precisionAtN;
            this.ndcgAtN = ndcgAtN;
            this.topNNoiseRate = topNNoiseRate;
            this.majorEventTotal = majorEventTotal;
            this.majorEventMissed = majorEventMissed;
            this.topNClusters = List.copyOf(Objects.requireNonNull(topNClusters, "topNClusters"));
        }

        public String getWindowStart() {
            return windowStart;
        }

        public String getWindowEnd() {
            return windowEnd;
        }

        public int getLabeledClusters() {
            return labeledClusters;
        }

        public int getRankableClusters() {
            return rankableClusters;
        }

        public int getMissingScores() {
            return missingScores;
        }

        public double getPrecisionAtN() {
            return precisionAtN;
        }

        public double getNdcgAtN() {
            return ndcgAtN;
        }

        public double getTopNNoiseRate() {
            return topNNoiseRate;
        }

        public int getMajorEventTotal() {
            return majorEventTotal;
        }

        public int getMajorEventMissed() {
            return majorEventMissed;
        }

        public List<RankedCluster> getTopNClusters() {
            return topNClusters;
        }
    }

    /**
     * One cluster's position in a ranked top-N list.
     */
    public static final class RankedCluster {
        private final long clusterId;
        private final int rank;
        private final double totalScore;
        private final String relevance;    // null when unlabeled
        private final Boolean isMajorEvent;

        public RankedCluster(
                long clusterId,
                int rank,
                double totalScore,
                String relevance,
                Boolean isMajorEvent
        ) {
            this.clusterId = clusterId;
            this.rank = rank;
            this.totalScore = totalScore;
            this.relevance = relevance;
            this.isMajorEvent = isMajorEvent;
        }

        public long getClusterId() {
            return clusterId;
        }

        public int getRank() {
            return rank;
        }

        public double getTotalScore() {
            return totalScore;
        }

        public String getRelevance() {
            return relevance;
        }

        public Boolean getIsMajorEvent() {
            return isMajorEvent;
        }
    }
}
