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
import com.airadar.item.mapper.HotItemMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RealDataClusterEvaluationService}. Uses plain Mockito
 * so the suite runs without Testcontainers. Verifies TP/FP/TN/FN classification
 * for both V1 ({@code hot_cluster_item}) and V2 ({@code cluster_match_decision})
 * and the NaN-safe metric calculations.
 */
class RealDataClusterEvaluationServiceTest {

    private EvaluationCaseMapper caseMapper;
    private HotItemMapper hotItemMapper;
    private HotClusterItemMapper clusterItemMapper;
    private ClusterMatchDecisionMapper decisionMapper;
    private RealDataClusterEvaluationService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        caseMapper = mock(EvaluationCaseMapper.class);
        hotItemMapper = mock(HotItemMapper.class);
        clusterItemMapper = mock(HotClusterItemMapper.class);
        decisionMapper = mock(ClusterMatchDecisionMapper.class);
        service = new RealDataClusterEvaluationService(
                caseMapper, hotItemMapper, clusterItemMapper, decisionMapper);
        mapper = new ObjectMapper();
    }

    @Test
    void rejectsUnsupportedStrategy() {
        assertThatThrownBy(() -> service.evaluate(1L, "v3-imaginary", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v3-imaginary");
    }

    @Test
    void emptyDatasetReturnsZeroReport() {
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ClusterPairEvaluationReport report = service.evaluate(
                1L, RuleBasedClusterService.RULE_VERSION, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getTotalPairs()).isZero();
        assertThat(report.getPairwisePrecision()).isNaN();
        assertThat(report.getPairwiseRecall()).isNaN();
        assertThat(report.getPairwiseF1()).isNaN();
        assertThat(report.getFalseMergeRate()).isNaN();
        assertThat(report.getFalseSplitRate()).isNaN();
        assertThat(report.getReviewRequiredRate()).isZero();
    }

    @Test
    void v1StrategyClassifiesTpFnFpTn() throws Exception {
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(pairCase(1L, "must-merge-ok", 11L, 12L, "MUST_MERGE", "MODEL_RELEASE"));
        cases.add(pairCase(2L, "must-merge-split", 21L, 22L, "MUST_MERGE", "MODEL_RELEASE"));
        cases.add(pairCase(3L, "must-not-merge-apart", 31L, 32L, "MUST_NOT_MERGE", "OTHER"));
        cases.add(pairCase(4L, "must-not-merge-wrong", 41L, 42L, "MUST_NOT_MERGE", "OTHER"));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);

        // V1 cluster memberships:
        //  11 -> cluster 100, 12 -> cluster 100   => TP (merged correctly)
        //  21 -> cluster 200, 22 -> cluster 201   => FN (should merge but split)
        //  31 -> cluster 300, 32 -> cluster 301   => TN (kept apart)
        //  41 -> cluster 400, 42 -> cluster 400   => FP (wrongly merged)
        when(clusterItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                membership(11L, 100L), membership(12L, 100L),
                membership(21L, 200L), membership(22L, 201L),
                membership(31L, 300L), membership(32L, 301L),
                membership(41L, 400L), membership(42L, 400L)
        ));

        ClusterPairEvaluationReport report = service.evaluate(
                1L, RuleBasedClusterService.RULE_VERSION, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getTruePositives()).isEqualTo(1);
        assertThat(report.getFalseNegatives()).isEqualTo(1);
        assertThat(report.getTrueNegatives()).isEqualTo(1);
        assertThat(report.getFalsePositives()).isEqualTo(1);

        // precision = TP / (TP + FP) = 1/2
        assertThat(report.getPairwisePrecision()).isEqualTo(0.5);
        // recall = TP / (TP + FN) = 1/2
        assertThat(report.getPairwiseRecall()).isEqualTo(0.5);
        // F1 = 2 * 0.5 * 0.5 / (0.5 + 0.5) = 0.5
        assertThat(report.getPairwiseF1()).isEqualTo(0.5);
        // falseMergeRate = FP / (FP + TN) = 1/2
        assertThat(report.getFalseMergeRate()).isEqualTo(0.5);
        // falseSplitRate = FN / (TP + FN) = 1/2
        assertThat(report.getFalseSplitRate()).isEqualTo(0.5);
        // V1 never produces REVIEW_REQUIRED
        assertThat(report.getReviewRequiredItems()).isZero();
        assertThat(report.getReviewRequiredRate()).isZero();

        // Category slices: MODEL_RELEASE has TP+FN, OTHER has TN+FP
        assertThat(report.getSlicesByCategory()).containsKeys("MODEL_RELEASE", "OTHER");
        assertThat(report.getSlicesByCategory().get("MODEL_RELEASE").getTruePositives()).isEqualTo(1);
        assertThat(report.getSlicesByCategory().get("MODEL_RELEASE").getFalseNegatives()).isEqualTo(1);
        assertThat(report.getSlicesByCategory().get("OTHER").getFalsePositives()).isEqualTo(1);
        assertThat(report.getSlicesByCategory().get("OTHER").getTrueNegatives()).isEqualTo(1);
    }

    @Test
    void v2StrategyResolvesClusterFromAcceptedDecisions() throws Exception {
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(pairCase(1L, "v2-merged", 51L, 52L, "MUST_MERGE", "MODEL_RELEASE"));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);

        // For V2, decisionMapper returns ACCEPTED with candidate_cluster_id.
        when(decisionMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> {
            // The same selectList is called for both resolveViaMatchDecision (LIMIT 1)
            // and findReviewRequiredItems. Differentiate by inspecting the wrapper SQL.
            // For unit test simplicity, return ACCEPTED rows only.
            return List.of(decision(51L, 500L, "ACCEPTED"), decision(52L, 500L, "ACCEPTED"));
        });

        ClusterPairEvaluationReport report = service.evaluate(
                1L, EventRuleClusterStrategy.RULE_VERSION, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getTruePositives()).isEqualTo(1);
        assertThat(report.getPairwisePrecision()).isEqualTo(1.0);
        assertThat(report.getPairwiseRecall()).isEqualTo(1.0);
        assertThat(report.getPairwiseF1()).isEqualTo(1.0);
    }

    @Test
    void v2StrategyReportsUnresolvedWhenNoAcceptedDecision() throws Exception {
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(pairCase(1L, "v2-no-decision", 61L, 62L, "MUST_MERGE", "MODEL_RELEASE"));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);

        // No ACCEPTED decisions returned for these items.
        when(decisionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ClusterPairEvaluationReport report = service.evaluate(
                1L, EventRuleClusterStrategy.RULE_VERSION, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getUnresolvedCount()).isEqualTo(1);
        assertThat(report.getTruePositives()).isZero();
        assertThat(report.getPairwisePrecision()).isNaN();
        assertThat(report.getPairwiseRecall()).isNaN();
    }

    @Test
    void ambiguousExpectationIsSkipped() throws Exception {
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(pairCase(1L, "ambiguous", 71L, 72L, "REVIEW_IF_AMBIGUOUS", "OTHER"));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);
        when(clusterItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                membership(71L, 700L), membership(72L, 700L)));

        ClusterPairEvaluationReport report = service.evaluate(
                1L, RuleBasedClusterService.RULE_VERSION, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getAmbiguousCount()).isEqualTo(1);
        assertThat(report.getTotalPairs()).isEqualTo(1);
        // Precision/Recall NaN because all pairs are skipped
        assertThat(report.getPairwisePrecision()).isNaN();
        assertThat(report.getPairwiseRecall()).isNaN();
    }

    private EvaluationCaseEntity pairCase(
            long id, String code, long itemA, long itemB,
            String expectation, String category) throws Exception {
        EvaluationCaseEntity entity = new EvaluationCaseEntity();
        entity.setId(id);
        entity.setCaseCode(code);
        entity.setCaseType(EvaluationCaseType.CLUSTER_PAIR_EXPECTATION);
        entity.setEnabled(true);
        JsonNode target = mapper.readTree(String.format(
                "{\"pairKey\":\"%s\",\"itemA\":{\"hotItemId\":%d,\"title\":\"t-a\"},"
                        + "\"itemB\":{\"hotItemId\":%d,\"title\":\"t-b\"}}",
                code, itemA, itemB));
        JsonNode expected = mapper.readTree(String.format(
                "{\"expectation\":\"%s\",\"category\":\"%s\"}", expectation, category));
        entity.setTargetPayload(target);
        entity.setExpectedPayload(expected);
        return entity;
    }

    private HotClusterItemEntity membership(long itemId, long clusterId) {
        HotClusterItemEntity entity = new HotClusterItemEntity();
        entity.setHotItemId(itemId);
        entity.setHotClusterId(clusterId);
        return entity;
    }

    private ClusterMatchDecisionEntity decision(long itemId, long clusterId, String decision) {
        ClusterMatchDecisionEntity entity = new ClusterMatchDecisionEntity();
        entity.setHotItemId(itemId);
        entity.setCandidateClusterId(clusterId);
        entity.setDecision(decision);
        entity.setRuleVersion(EventRuleClusterStrategy.RULE_VERSION);
        return entity;
    }
}
