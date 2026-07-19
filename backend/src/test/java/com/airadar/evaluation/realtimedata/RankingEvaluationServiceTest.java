package com.airadar.evaluation.realtimedata;

import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RankingEvaluationService}. Uses plain Mockito. Verifies
 * NDCG@N, Precision@N, Top-N noise rate, major-event miss rate, and V1/V2
 * top-N symmetric-difference calculation.
 */
class RankingEvaluationServiceTest {

    private EvaluationCaseMapper caseMapper;
    private HotScoreMapper scoreMapper;
    private RankingEvaluationService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        caseMapper = mock(EvaluationCaseMapper.class);
        scoreMapper = mock(HotScoreMapper.class);
        service = new RankingEvaluationService(caseMapper, scoreMapper);
        mapper = new ObjectMapper();
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThatThrownBy(() -> service.evaluate(1L, "v9-imaginary", 10, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v9-imaginary");
    }

    @Test
    void rejectsNonPositiveTopN() {
        assertThatThrownBy(() -> service.evaluate(1L, "hn-score-v1", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topN");
    }

    @Test
    void emptyDatasetReturnsZeroReport() {
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        RankingEvaluationReport report = service.evaluate(
                1L, "hn-score-v1", 10, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getWindowCount()).isZero();
        assertThat(report.getLabeledClusterCount()).isZero();
        assertThat(report.getPrecisionAtN()).isNaN();
        assertThat(report.getNdcgAtN()).isNaN();
        assertThat(report.getMajorEventMissRate()).isNaN();
    }

    @Test
    void computesPrecisionAndNdcgForSingleWindow() throws Exception {
        // Window 2026-07-18: 3 labeled clusters
        //   cluster 100: HIGHLY_RELEVANT (gain 3), V1 score 95
        //   cluster 200: RELEVANT         (gain 2), V1 score 80
        //   cluster 300: NOISE            (gain 0), V1 score 70
        // top-2 = [100, 200], both relevant => precision@2 = 1.0
        // DCG@2 = (2^3-1)/log2(2) + (2^2-1)/log2(3) = 7/1 + 3/1.585 = 7 + 1.893 = 8.893
        // IDCG@2 = same (ideal order already matches) = 8.893
        // NDCG@2 = 1.0
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(rankingCase(1L, "100", 100L, "HIGHLY_RELEVANT", false));
        cases.add(rankingCase(2L, "200", 200L, "RELEVANT", false));
        cases.add(rankingCase(3L, "300", 300L, "NOISE", false));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);
        when(scoreMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                score(100L, "hn-score-v1", "95"),
                score(200L, "hn-score-v1", "80"),
                score(300L, "hn-score-v1", "70")
        ));

        RankingEvaluationReport report = service.evaluate(
                1L, "hn-score-v1", 2, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getWindowCount()).isEqualTo(1);
        assertThat(report.getPrecisionAtN()).isEqualTo(1.0);
        assertThat(report.getNdcgAtN()).isCloseTo(1.0, within(0.0001));
        assertThat(report.getTopNNoiseRate()).isZero();
    }

    @Test
    void detectsNoiseAndMajorEventMiss() throws Exception {
        // Window: cluster 100 HIGHLY (major), cluster 200 NOISE, cluster 300 NOISE
        // top-2 by score: cluster 100 (90), cluster 200 (85)
        // HIGHLY_RELEVANT counts as relevant (gain 3 >= 2) -> precision = 1/2
        // top-2 noise rate = 1/2 (cluster 200)
        // major event cluster 100 IS in top-2 -> 0 misses
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(rankingCase(1L, "100", 100L, "HIGHLY_RELEVANT", true));
        cases.add(rankingCase(2L, "200", 200L, "NOISE", false));
        cases.add(rankingCase(3L, "300", 300L, "NOISE", false));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);
        when(scoreMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                score(100L, "hn-score-v1", "90"),
                score(200L, "hn-score-v1", "85"),
                score(300L, "hn-score-v1", "60")
        ));

        RankingEvaluationReport report = service.evaluate(
                1L, "hn-score-v1", 2, Instant.parse("2026-07-19T10:00:00Z"));

        // top-2 = [100, 200]; 100 is HIGHLY (relevant), 200 is NOISE.
        assertThat(report.getPrecisionAtN()).isEqualTo(0.5);
        assertThat(report.getTopNNoiseRate()).isEqualTo(0.5);
        assertThat(report.getMajorEventMissed()).isZero();
        assertThat(report.getMajorEventMissRate()).isZero();
    }

    @Test
    void reportsMissingScores() throws Exception {
        // cluster 100 has V1 score; cluster 200 has NO V1 score
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(rankingCase(1L, "100", 100L, "RELEVANT", false));
        cases.add(rankingCase(2L, "200", 200L, "RELEVANT", false));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);
        when(scoreMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                score(100L, "hn-score-v1", "50")
        ));

        RankingEvaluationReport report = service.evaluate(
                1L, "hn-score-v1", 10, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getMissingScoreCount()).isEqualTo(1);
        assertThat(report.getWindowReports().get(0).getMissingScores()).isEqualTo(1);
    }

    @Test
    void computesV2RankingDiffVsV1() throws Exception {
        // Two clusters, V1 ranks 100 > 200, V2 ranks 200 > 100
        // V1 top-1 = {100}, V2 top-1 = {200} -> symmetric diff = 2
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(rankingCase(1L, "100", 100L, "RELEVANT", false));
        cases.add(rankingCase(2L, "200", 200L, "RELEVANT", false));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);

        // Service makes two scoreMapper calls for V2 eval:
        //   1st: fetchLatestScores(allClusters, "hn-score-v1") for the V1 diff base
        //   2nd: fetchLatestScores(windowClusters, "cross-source-score-v2") for ranking
        when(scoreMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(
                        score(100L, "hn-score-v1", "80"),
                        score(200L, "hn-score-v1", "70")))
                .thenReturn(List.of(
                        score(100L, "cross-source-score-v2", "60"),
                        score(200L, "cross-source-score-v2", "90")));

        RankingEvaluationReport report = service.evaluate(
                1L, "cross-source-score-v2", 1, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getRankingDiffVsV1TopN()).isEqualTo(2);
    }

    @Test
    void v1ReportHasNoRankingDiff() throws Exception {
        List<EvaluationCaseEntity> cases = new ArrayList<>();
        cases.add(rankingCase(1L, "100", 100L, "RELEVANT", false));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(cases);
        when(scoreMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                score(100L, "hn-score-v1", "50")
        ));

        RankingEvaluationReport report = service.evaluate(
                1L, "hn-score-v1", 1, Instant.parse("2026-07-19T10:00:00Z"));

        assertThat(report.getRankingDiffVsV1TopN()).isNull();
    }

    private EvaluationCaseEntity rankingCase(
            long id, String clusterKey, long clusterId,
            String relevance, boolean isMajor) throws Exception {
        EvaluationCaseEntity entity = new EvaluationCaseEntity();
        entity.setId(id);
        entity.setCaseCode(clusterKey);
        entity.setCaseType(EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION);
        entity.setEnabled(true);
        JsonNode target = mapper.readTree(String.format(
                "{\"windowStart\":\"2026-07-18T00:00:00Z\",\"windowEnd\":\"2026-07-18T23:59:59Z\","
                        + "\"clusterId\":%d}", clusterId));
        JsonNode expected = mapper.readTree(String.format(
                "{\"relevance\":\"%s\",\"isMajorEvent\":%s}", relevance, isMajor));
        entity.setTargetPayload(target);
        entity.setExpectedPayload(expected);
        return entity;
    }

    private HotScoreEntity score(long clusterId, String version, String scoreValue) {
        HotScoreEntity entity = new HotScoreEntity();
        entity.setHotClusterId(clusterId);
        entity.setScoringVersion(version);
        entity.setTotalScore(new BigDecimal(scoreValue));
        return entity;
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
