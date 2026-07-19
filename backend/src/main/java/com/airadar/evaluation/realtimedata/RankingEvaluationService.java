package com.airadar.evaluation.realtimedata;

import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates {@link EvaluationCaseType#RANKING_RELEVANCE_EXPECTATION} cases
 * against the persisted {@code hot_score} rows for either V1
 * ({@code hn-score-v1}) or V2 ({@code cross-source-score-v2}).
 *
 * <p>The ranked universe per window is the set of clusters labeled in that
 * window. This is a deliberate simplification: the goal is to score how well
 * the scorer orders the clusters we have ground-truth relevance for. Pulling
 * every persisted cluster into the ranking would dilute precision with
 * unlabeled entries and is left for a future iteration if needed.
 *
 * <p><b>Ranking pipeline per window</b>:
 * <ol>
 *   <li>Load all {@code RANKING_RELEVANCE_EXPECTATION} cases for the dataset,
 *       grouped by {@code windowStart}.</li>
 *   <li>For each cluster id appearing in the window, fetch the latest
 *       {@code hot_score} row by {@code scoringVersion}. Missing scores are
 *       reported in {@code missingScoreCount}.</li>
 *   <li>Sort the scoreable clusters by {@code totalScore} desc; take top N.</li>
 *   <li>Compute per-window metrics, then roll up across windows.</li>
 * </ol>
 *
 * <p>When the requested scoring version is {@code cross-source-score-v2}, the
 * service also fetches the V1 top-N per window to compute
 * {@code rankingDiffVsV1TopN}.
 */
@Service
public class RankingEvaluationService {

    public static final String V1_SCORING_VERSION = "hn-score-v1";
    public static final String V2_SCORING_VERSION = "cross-source-score-v2";

    private static final Set<String> SUPPORTED_VERSIONS = Set.of(V1_SCORING_VERSION, V2_SCORING_VERSION);

    private static final Map<String, Integer> GAIN_BY_RELEVANCE = Map.of(
            "HIGHLY_RELEVANT", 3,
            "RELEVANT", 2,
            "MARGINALLY_RELEVANT", 1,
            "NOISE", 0
    );

    private final EvaluationCaseMapper caseMapper;
    private final HotScoreMapper scoreMapper;

    public RankingEvaluationService(EvaluationCaseMapper caseMapper, HotScoreMapper scoreMapper) {
        this.caseMapper = caseMapper;
        this.scoreMapper = scoreMapper;
    }

    @Transactional(readOnly = true)
    public RankingEvaluationReport evaluate(
            long datasetId,
            String scoringVersion,
            int topN,
            Instant evaluatedAt
    ) {
        if (!SUPPORTED_VERSIONS.contains(scoringVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported scoring version: " + scoringVersion
                            + ". Supported: " + SUPPORTED_VERSIONS);
        }
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive, got " + topN);
        }

        List<EvaluationCaseEntity> cases = caseMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseEntity>()
                        .eq(EvaluationCaseEntity::getDatasetId, datasetId)
                        .eq(EvaluationCaseEntity::getCaseType, EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION)
                        .eq(EvaluationCaseEntity::getEnabled, Boolean.TRUE)
                        .orderByAsc(EvaluationCaseEntity::getId)
        );

        Map<String, List<LabeledCluster>> byWindow = groupByWindow(cases);

        Map<Long, Double> v1TopScoresByCluster = scoringVersion.equals(V2_SCORING_VERSION)
                ? fetchLatestScores(collectAllClusterIds(byWindow), V1_SCORING_VERSION)
                : Map.of();

        int totalLabeled = 0;
        int totalMissing = 0;
        int totalTopN = 0;
        int totalRelevantInTopN = 0;
        int totalNoiseInTopN = 0;
        double totalDcg = 0.0;
        double totalIdcg = 0.0;
        int majorEventTotal = 0;
        int majorEventMissed = 0;
        int totalSetDiff = 0;
        List<RankingEvaluationReport.WindowReport> windowReports = new ArrayList<>(byWindow.size());

        for (Map.Entry<String, List<LabeledCluster>> window : byWindow.entrySet()) {
            List<LabeledCluster> labeled = window.getValue();
            totalLabeled += labeled.size();

            Set<Long> clusterIds = new HashSet<>();
            for (LabeledCluster c : labeled) {
                clusterIds.add(c.clusterId);
            }
            Map<Long, Double> scores = fetchLatestScores(clusterIds, scoringVersion);

            // Split labeled into rankable (have score) vs missing
            List<ScoredCluster> rankable = new ArrayList<>(labeled.size());
            int missing = 0;
            for (LabeledCluster c : labeled) {
                Double score = scores.get(c.clusterId);
                if (score == null) {
                    missing++;
                } else {
                    rankable.add(new ScoredCluster(c, score));
                }
            }
            totalMissing += missing;

            rankable.sort(Comparator.comparingDouble(ScoredCluster::score).reversed());
            int effectiveN = Math.min(topN, rankable.size());

            // Compute per-window DCG, IDCG, precision, noise rate, major-event miss.
            int relevantInTop = 0;
            int noiseInTop = 0;
            double dcg = 0.0;
            List<RankingEvaluationReport.RankedCluster> rankedTop = new ArrayList<>(effectiveN);
            Set<Long> topNClusterIds = new HashSet<>();
            for (int i = 0; i < effectiveN; i++) {
                ScoredCluster sc = rankable.get(i);
                int rank = i + 1;
                double gain = sc.labeled.gain;
                dcg += (Math.pow(2.0, gain) - 1.0) / log2(rank + 1);
                if (gain >= 2) {
                    relevantInTop++;
                }
                if (gain == 0) {
                    noiseInTop++;
                }
                topNClusterIds.add(sc.labeled.clusterId);
                rankedTop.add(new RankingEvaluationReport.RankedCluster(
                        sc.labeled.clusterId, rank, sc.score, sc.labeled.relevance, sc.labeled.isMajorEvent));
            }
            totalTopN += effectiveN;
            totalRelevantInTopN += relevantInTop;
            totalNoiseInTopN += noiseInTop;
            totalDcg += dcg;

            // IDCG: re-sort by gain desc and discount the top effectiveN
            List<ScoredCluster> byGain = new ArrayList<>(rankable);
            byGain.sort(Comparator.comparingInt(s -> -s.labeled.gain));
            double idcg = 0.0;
            for (int i = 0; i < effectiveN; i++) {
                int rank = i + 1;
                double gain = byGain.get(i).labeled.gain;
                idcg += (Math.pow(2.0, gain) - 1.0) / log2(rank + 1);
            }
            totalIdcg += idcg;

            int windowMajorTotal = 0;
            int windowMajorMissed = 0;
            for (LabeledCluster c : labeled) {
                if (Boolean.TRUE.equals(c.isMajorEvent)) {
                    windowMajorTotal++;
                    if (!topNClusterIds.contains(c.clusterId)) {
                        windowMajorMissed++;
                    }
                }
            }
            majorEventTotal += windowMajorTotal;
            majorEventMissed += windowMajorMissed;

            double windowPrecision = totalSafe(relevantInTop, effectiveN);
            double windowNoise = totalSafe(noiseInTop, effectiveN);
            double windowNdcg = idcg == 0.0 ? Double.NaN : dcg / idcg;

            // V2 only: set diff vs V1 top-N
            if (scoringVersion.equals(V2_SCORING_VERSION)) {
                Set<Long> v1TopIds = computeV1TopN(labeled, v1TopScoresByCluster, topN);
                totalSetDiff += symmetricDifference(topNClusterIds, v1TopIds);
            }

            String windowEnd = labeled.isEmpty() ? null : labeled.get(0).windowEnd;
            windowReports.add(new RankingEvaluationReport.WindowReport(
                    window.getKey(), windowEnd,
                    labeled.size(), rankable.size(), missing,
                    windowPrecision, windowNdcg, windowNoise,
                    windowMajorTotal, windowMajorMissed,
                    rankedTop));
        }

        double overallPrecision = totalSafe(totalRelevantInTopN, totalTopN);
        double overallNoise = totalSafe(totalNoiseInTopN, totalTopN);
        double overallNdcg = totalIdcg == 0.0 ? Double.NaN : totalDcg / totalIdcg;
        double majorMissRate = totalSafe(majorEventMissed, majorEventTotal);

        Integer diffVsV1 = scoringVersion.equals(V2_SCORING_VERSION) ? totalSetDiff : null;

        return new RankingEvaluationReport(
                scoringVersion, topN, evaluatedAt,
                byWindow.size(), totalLabeled, totalMissing,
                overallPrecision, overallNdcg, overallNoise,
                majorEventTotal, majorEventMissed, majorMissRate,
                diffVsV1, windowReports);
    }

    private Map<String, List<LabeledCluster>> groupByWindow(List<EvaluationCaseEntity> cases) {
        Map<String, List<LabeledCluster>> out = new LinkedHashMap<>();
        for (EvaluationCaseEntity c : cases) {
            String windowStart = c.getTargetPayload().path("windowStart").asText();
            long clusterId = c.getTargetPayload().path("clusterId").asLong();
            String windowEnd = c.getTargetPayload().path("windowEnd").asText();
            String relevance = c.getExpectedPayload().path("relevance").asText();
            boolean isMajor = c.getExpectedPayload().path("isMajorEvent").asBoolean();
            Integer gain = GAIN_BY_RELEVANCE.get(relevance);
            if (gain == null) {
                // Unknown relevance label - skip
                continue;
            }
            if (windowStart.isBlank() || clusterId <= 0) {
                continue;
            }
            out.computeIfAbsent(windowStart, k -> new ArrayList<>())
                    .add(new LabeledCluster(clusterId, windowEnd, relevance, gain, isMajor));
        }
        return out;
    }

    private Set<Long> collectAllClusterIds(Map<String, List<LabeledCluster>> byWindow) {
        Set<Long> ids = new HashSet<>();
        for (List<LabeledCluster> list : byWindow.values()) {
            for (LabeledCluster c : list) {
                ids.add(c.clusterId);
            }
        }
        return ids;
    }

    private Map<Long, Double> fetchLatestScores(Set<Long> clusterIds, String scoringVersion) {
        if (clusterIds.isEmpty()) {
            return Map.of();
        }
        // Order by id desc so the first row per cluster is the most recently inserted score
        // for that version. putIfAbsent keeps the latest and discards older duplicates.
        List<HotScoreEntity> ordered = scoreMapper.selectList(
                new LambdaQueryWrapper<HotScoreEntity>()
                        .in(HotScoreEntity::getHotClusterId, clusterIds)
                        .eq(HotScoreEntity::getScoringVersion, scoringVersion)
                        .orderByDesc(HotScoreEntity::getId)
        );
        Map<Long, Double> latest = new HashMap<>(clusterIds.size());
        for (HotScoreEntity row : ordered) {
            if (row.getTotalScore() == null) {
                continue;
            }
            latest.putIfAbsent(row.getHotClusterId(), row.getTotalScore().doubleValue());
        }
        return latest;
    }

    private Set<Long> computeV1TopN(List<LabeledCluster> labeled, Map<Long, Double> v1Scores, int topN) {
        List<ScoredCluster> rankable = new ArrayList<>(labeled.size());
        for (LabeledCluster c : labeled) {
            Double score = v1Scores.get(c.clusterId);
            if (score != null) {
                rankable.add(new ScoredCluster(c, score));
            }
        }
        rankable.sort(Comparator.comparingDouble(ScoredCluster::score).reversed());
        int effectiveN = Math.min(topN, rankable.size());
        Set<Long> ids = new HashSet<>(effectiveN);
        for (int i = 0; i < effectiveN; i++) {
            ids.add(rankable.get(i).labeled.clusterId);
        }
        return ids;
    }

    private static int symmetricDifference(Set<Long> a, Set<Long> b) {
        int diff = 0;
        Set<Long> aCopy = new HashSet<>(a);
        aCopy.removeAll(b);
        diff += aCopy.size();
        Set<Long> bCopy = new HashSet<>(b);
        bCopy.removeAll(a);
        diff += bCopy.size();
        return diff;
    }

    private static double totalSafe(int numerator, int denominator) {
        if (denominator == 0) {
            return Double.NaN;
        }
        return (double) numerator / denominator;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private record LabeledCluster(
            long clusterId,
            String windowEnd,
            String relevance,
            int gain,
            boolean isMajorEvent
    ) {
    }

    private record ScoredCluster(LabeledCluster labeled, double score) {
    }
}
