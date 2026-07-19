package com.airadar.evaluation.realtimedata;

import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.service.RuleBasedClusterService;
import com.airadar.cluster.strategy.ClusterMatchDecisionEntity;
import com.airadar.cluster.strategy.ClusterMatchDecisionMapper;
import com.airadar.cluster.strategy.EventRuleClusterStrategy;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates {@link EvaluationCaseType#CLUSTER_PAIR_EXPECTATION} cases against
 * the real-data cluster state produced by either V1 ({@code hn-rule-v1}) or
 * V2 shadow ({@code event-rule-v2}).
 *
 * <p><b>Unlike {@code ClusterEvaluationService}</b>, this service does NOT
 * truncate any tables. It reads whatever real cluster state is currently
 * persisted for each item. This is what makes the report meaningful for
 * deciding whether V2 can safely take over from V1: both strategies are
 * scored against the same labeled expectations on production-shaped data.
 *
 * <p>Strategy resolution rules:
 * <ul>
 *   <li>{@code hn-rule-v1}: cluster id is read from {@code hot_cluster_item}
 *       rows where {@code removed_at IS NULL}. These are the authoritative
 *       online memberships produced by the V1 strategy.</li>
 *   <li>{@code event-rule-v2}: cluster id is read from the most recent
 *       {@code ACCEPTED} row in {@code cluster_match_decision} with
 *       {@code rule_version = 'event-rule-v2'} for that item. If no ACCEPTED
 *       row exists, the item has no V2 cluster assignment (counted as
 *       UNRESOLVED).</li>
 * </ul>
 *
 * <p>Pair classification against {@code expected_payload.expectation}:
 * <ul>
 *   <li>{@code MUST_MERGE}: same cluster → TP; different cluster → FN</li>
 *   <li>{@code MUST_NOT_MERGE}: same cluster → FP; different cluster → TN</li>
 *   <li>{@code REVIEW_IF_AMBIGUOUS}: not counted in P/R/F1; reported as SKIP</li>
 *   <li>Items with unresolved cluster id → UNRESOLVED (not counted in P/R/F1)</li>
 * </ul>
 */
@Service
public class RealDataClusterEvaluationService {

    private static final Set<String> SUPPORTED_STRATEGIES = Set.of(
            RuleBasedClusterService.RULE_VERSION,
            EventRuleClusterStrategy.RULE_VERSION
    );

    private final EvaluationCaseMapper caseMapper;
    private final HotItemMapper hotItemMapper;
    private final HotClusterItemMapper clusterItemMapper;
    private final ClusterMatchDecisionMapper decisionMapper;

    public RealDataClusterEvaluationService(
            EvaluationCaseMapper caseMapper,
            HotItemMapper hotItemMapper,
            HotClusterItemMapper clusterItemMapper,
            ClusterMatchDecisionMapper decisionMapper
    ) {
        this.caseMapper = caseMapper;
        this.hotItemMapper = hotItemMapper;
        this.clusterItemMapper = clusterItemMapper;
        this.decisionMapper = decisionMapper;
    }

    @Transactional(readOnly = true)
    public ClusterPairEvaluationReport evaluate(
            long datasetId,
            String strategyVersion,
            Instant evaluatedAt
    ) {
        if (!SUPPORTED_STRATEGIES.contains(strategyVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported strategy version: " + strategyVersion
                            + ". Supported: " + SUPPORTED_STRATEGIES);
        }

        List<EvaluationCaseEntity> cases = caseMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseEntity>()
                        .eq(EvaluationCaseEntity::getDatasetId, datasetId)
                        .eq(EvaluationCaseEntity::getCaseType, EvaluationCaseType.CLUSTER_PAIR_EXPECTATION)
                        .eq(EvaluationCaseEntity::getEnabled, Boolean.TRUE)
                        .orderByAsc(EvaluationCaseEntity::getId)
        );

        Set<Long> distinctItemIds = new java.util.HashSet<>();
        for (EvaluationCaseEntity c : cases) {
            Long a = readItemId(c.getTargetPayload().path("itemA"));
            Long b = readItemId(c.getTargetPayload().path("itemB"));
            if (a != null) {
                distinctItemIds.add(a);
            }
            if (b != null) {
                distinctItemIds.add(b);
            }
        }

        Map<Long, Long> clusterByItem = resolveClusterByItem(distinctItemIds, strategyVersion);
        Set<Long> reviewRequiredItems = strategyVersion.equals(EventRuleClusterStrategy.RULE_VERSION)
                ? findReviewRequiredItems(distinctItemIds)
                : Set.of();

        int tp = 0;
        int fp = 0;
        int tn = 0;
        int fn = 0;
        int ambiguous = 0;
        int unresolved = 0;
        Map<String, int[]> slices = new HashMap<>();
        List<ClusterPairEvaluationReport.PairCaseResult> perCase = new ArrayList<>(cases.size());

        for (EvaluationCaseEntity c : cases) {
            Long itemA = readItemId(c.getTargetPayload().path("itemA"));
            Long itemB = readItemId(c.getTargetPayload().path("itemB"));
            Long clusterA = itemA == null ? null : clusterByItem.get(itemA);
            Long clusterB = itemB == null ? null : clusterByItem.get(itemB);
            String expectation = c.getExpectedPayload().path("expectation").asText();
            String category = c.getExpectedPayload().path("category").asText("OTHER");

            String outcome;
            String reason;
            if (itemA == null || itemB == null) {
                outcome = "UNRESOLVED";
                reason = "could not resolve hot_item id from target_payload";
                unresolved++;
            } else if (clusterA == null || clusterB == null) {
                outcome = "UNRESOLVED";
                reason = "no cluster assignment for strategy " + strategyVersion
                        + " (itemA=" + itemA + " cluster=" + clusterA
                        + ", itemB=" + itemB + " cluster=" + clusterB + ")";
                unresolved++;
            } else if ("REVIEW_IF_AMBIGUOUS".equals(expectation)) {
                outcome = "SKIP";
                reason = "ambiguous expectation, excluded from P/R/F1";
                ambiguous++;
            } else {
                boolean actuallySame = clusterA.equals(clusterB);
                if ("MUST_MERGE".equals(expectation)) {
                    if (actuallySame) {
                        outcome = "TP";
                        reason = "merged into cluster " + clusterA;
                        tp++;
                        bumpSlice(slices, category, 0);
                    } else {
                        outcome = "FN";
                        reason = "split across clusters " + clusterA + " vs " + clusterB;
                        fn++;
                        bumpSlice(slices, category, 1);
                    }
                } else if ("MUST_NOT_MERGE".equals(expectation)) {
                    if (actuallySame) {
                        outcome = "FP";
                        reason = "wrongly merged into cluster " + clusterA;
                        fp++;
                        bumpSlice(slices, category, 2);
                    } else {
                        outcome = "TN";
                        reason = "kept apart (" + clusterA + " vs " + clusterB + ")";
                        tn++;
                        bumpSlice(slices, category, 3);
                    }
                } else {
                    outcome = "SKIP";
                    reason = "unknown expectation " + expectation;
                    ambiguous++;
                }
            }

            perCase.add(new ClusterPairEvaluationReport.PairCaseResult(
                    c.getCaseCode(), expectation, category,
                    itemA, itemB, clusterA, clusterB, outcome, reason));
        }

        double precision = safeDiv(tp, tp + fp);
        double recall = safeDiv(tp, tp + fn);
        double f1 = safeF1(precision, recall);
        double falseMergeRate = safeDiv(fp, fp + tn);
        double falseSplitRate = safeDiv(fn, tp + fn);
        double reviewRate = distinctItemIds.isEmpty()
                ? 0.0
                : (double) reviewRequiredItems.size() / distinctItemIds.size();

        Map<String, ClusterPairEvaluationReport.CategorySlice> sliceVOs = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : slices.entrySet()) {
            int[] arr = e.getValue();
            int catMustMerge = arr[0] + arr[1];
            int catMustNotMerge = arr[2] + arr[3];
            int catTp = arr[0];
            int catFn = arr[1];
            int catFp = arr[2];
            int catTn = arr[3];
            double catPrecision = safeDiv(catTp, catTp + catFp);
            double catRecall = safeDiv(catTp, catTp + catFn);
            sliceVOs.put(e.getKey(), new ClusterPairEvaluationReport.CategorySlice(
                    e.getKey(),
                    catMustMerge + catMustNotMerge,
                    catTp, catFp, catTn, catFn,
                    catPrecision, catRecall, safeF1(catPrecision, catRecall)
            ));
        }

        return new ClusterPairEvaluationReport(
                strategyVersion,
                evaluatedAt,
                cases.size(),
                tp, fp, tn, fn,
                ambiguous, unresolved,
                reviewRequiredItems.size(),
                precision, recall, f1,
                falseMergeRate, falseSplitRate,
                reviewRate,
                sliceVOs,
                perCase
        );
    }

    private Map<Long, Long> resolveClusterByItem(Set<Long> itemIds, String strategyVersion) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        if (RuleBasedClusterService.RULE_VERSION.equals(strategyVersion)) {
            return resolveViaHotClusterItem(itemIds);
        }
        return resolveViaMatchDecision(itemIds);
    }

    private Map<Long, Long> resolveViaHotClusterItem(Set<Long> itemIds) {
        List<HotClusterItemEntity> rows = clusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .in(HotClusterItemEntity::getHotItemId, itemIds)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        Map<Long, Long> out = new HashMap<>(rows.size());
        for (HotClusterItemEntity row : rows) {
            out.put(row.getHotItemId(), row.getHotClusterId());
        }
        return out;
    }

    private Map<Long, Long> resolveViaMatchDecision(Set<Long> itemIds) {
        Map<Long, Long> out = new HashMap<>();
        for (Long itemId : itemIds) {
            List<ClusterMatchDecisionEntity> decisions = decisionMapper.selectList(
                    new LambdaQueryWrapper<ClusterMatchDecisionEntity>()
                            .eq(ClusterMatchDecisionEntity::getHotItemId, itemId)
                            .eq(ClusterMatchDecisionEntity::getRuleVersion, EventRuleClusterStrategy.RULE_VERSION)
                            .eq(ClusterMatchDecisionEntity::getDecision, "ACCEPTED")
                            .isNotNull(ClusterMatchDecisionEntity::getCandidateClusterId)
                            .orderByDesc(ClusterMatchDecisionEntity::getDecidedAt)
                            .last("LIMIT 1")
            );
            if (!decisions.isEmpty()) {
                out.put(itemId, decisions.get(0).getCandidateClusterId());
            }
        }
        return out;
    }

    private Set<Long> findReviewRequiredItems(Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Set.of();
        }
        List<ClusterMatchDecisionEntity> rows = decisionMapper.selectList(
                new LambdaQueryWrapper<ClusterMatchDecisionEntity>()
                        .in(ClusterMatchDecisionEntity::getHotItemId, itemIds)
                        .eq(ClusterMatchDecisionEntity::getRuleVersion, EventRuleClusterStrategy.RULE_VERSION)
                        .eq(ClusterMatchDecisionEntity::getDecision, "REVIEW_REQUIRED")
        );
        Set<Long> out = new java.util.HashSet<>(rows.size());
        for (ClusterMatchDecisionEntity row : rows) {
            out.add(row.getHotItemId());
        }
        return out;
    }

    private Long readItemId(JsonNode itemNode) {
        if (itemNode == null || !itemNode.isObject()) {
            return null;
        }
        long hotItemId = itemNode.path("hotItemId").asLong();
        if (hotItemId > 0) {
            return hotItemId;
        }
        String sourceTypeRaw = itemNode.path("sourceType").asText();
        String externalId = itemNode.path("externalId").asText();
        if (sourceTypeRaw.isBlank() || externalId.isBlank()) {
            return null;
        }
        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(sourceTypeRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        List<HotItemEntity> matches = hotItemMapper.selectList(
                new LambdaQueryWrapper<HotItemEntity>()
                        .eq(HotItemEntity::getSourceType, sourceType)
                        .eq(HotItemEntity::getExternalId, externalId)
                        .last("LIMIT 1")
        );
        return matches.isEmpty() ? null : matches.get(0).getId();
    }

    private static void bumpSlice(Map<String, int[]> slices, String category, int bucket) {
        int[] arr = slices.computeIfAbsent(category, k -> new int[4]);
        arr[bucket]++;
    }

    private static double safeDiv(long numerator, long denominator) {
        if (denominator == 0) {
            return Double.NaN;
        }
        return (double) numerator / denominator;
    }

    private static double safeF1(double precision, double recall) {
        if (Double.isNaN(precision) || Double.isNaN(recall) || (precision + recall) == 0) {
            return Double.NaN;
        }
        return 2 * precision * recall / (precision + recall);
    }
}
