package com.airadar.evaluation.realtimedata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EvaluationReportWriter}. Verifies the five output
 * files exist with the expected fields, and that the Markdown summary
 * surfaces V1/V2 side-by-side metrics.
 */
class EvaluationReportWriterTest {

    @TempDir
    Path tempDir;

    private EvaluationReportWriter writer;
    private ObjectMapper parser;

    @BeforeEach
    void setUp() {
        writer = new EvaluationReportWriter(new ObjectMapper());
        parser = new ObjectMapper();
    }

    @Test
    void writesAllFourJsonsAndSummary() throws Exception {
        ClusterPairEvaluationReport clusterV1 = clusterReport("hn-rule-v1", 10, 8, 1, 1, 0);
        ClusterPairEvaluationReport clusterV2 = clusterReport("event-rule-v2", 10, 9, 0, 1, 0);
        RankingEvaluationReport rankingV1 = rankingReport("hn-score-v1", 10, 0.6, 0.55);
        RankingEvaluationReport rankingV2 = rankingReport("cross-source-score-v2", 10, 0.7, 0.68);

        EvaluationReportWriter.WrittenReports written = writer.write(
                clusterV1, clusterV2, rankingV1, rankingV2, tempDir);

        assertThat(written.clusterV1Json()).exists();
        assertThat(written.clusterV2Json()).exists();
        assertThat(written.rankingV1Json()).exists();
        assertThat(written.rankingV2Json()).exists();
        assertThat(written.summaryMarkdown()).exists();

        JsonNode clusterV1Node = parser.readTree(written.clusterV1Json().toFile());
        assertThat(clusterV1Node.get("strategyVersion").asText()).isEqualTo("hn-rule-v1");
        assertThat(clusterV1Node.get("totalPairs").asInt()).isEqualTo(10);
        assertThat(clusterV1Node.get("truePositives").asInt()).isEqualTo(8);

        JsonNode rankingV2Node = parser.readTree(written.rankingV2Json().toFile());
        assertThat(rankingV2Node.get("scoringVersion").asText()).isEqualTo("cross-source-score-v2");
        assertThat(rankingV2Node.get("topN").asInt()).isEqualTo(10);
    }

    @Test
    void summaryContainsV1V2SideBySide() throws Exception {
        ClusterPairEvaluationReport clusterV1 = clusterReport("hn-rule-v1", 4, 3, 0, 1, 0);
        ClusterPairEvaluationReport clusterV2 = clusterReport("event-rule-v2", 4, 4, 0, 0, 0);
        RankingEvaluationReport rankingV1 = rankingReport("hn-score-v1", 10, 0.5, 0.4);
        RankingEvaluationReport rankingV2 = rankingReport("cross-source-score-v2", 10, 0.6, 0.5);

        EvaluationReportWriter.WrittenReports written = writer.write(
                clusterV1, clusterV2, rankingV1, rankingV2, tempDir);
        String summary = Files.readString(written.summaryMarkdown());

        assertThat(summary).contains("Cluster Pair Evaluation");
        assertThat(summary).contains("Ranking Evaluation");
        assertThat(summary).contains("hn-rule-v1");
        assertThat(summary).contains("event-rule-v2");
        assertThat(summary).contains("hn-score-v1");
        assertThat(summary).contains("cross-source-score-v2");
        assertThat(summary).contains("Acceptance Gates");
    }

    @Test
    void skipsMissingReports() throws Exception {
        EvaluationReportWriter.WrittenReports written = writer.write(
                clusterReport("hn-rule-v1", 2, 2, 0, 0, 0),
                null,
                null,
                null,
                tempDir);

        assertThat(written.clusterV1Json()).exists();
        assertThat(written.clusterV2Json()).isNull();
        assertThat(written.rankingV1Json()).isNull();
        assertThat(written.rankingV2Json()).isNull();
        assertThat(written.summaryMarkdown()).exists();
    }

    @Test
    void buildMetricsPayloadCarriesAllAggregateFields() {
        ClusterPairEvaluationReport clusterV1 = clusterReport("hn-rule-v1", 4, 3, 0, 1, 0);
        RankingEvaluationReport rankingV1 = rankingReport("hn-score-v1", 10, 0.5, 0.4);

        JsonNode payload = writer.buildMetricsPayload(clusterV1, null, rankingV1, null);

        assertThat(payload.has("clusterV1")).isTrue();
        assertThat(payload.has("rankingV1")).isTrue();
        assertThat(payload.has("clusterV2")).isFalse();
        assertThat(payload.get("clusterV1").get("pairwisePrecision").asDouble()).isEqualTo(1.0);
        assertThat(payload.get("rankingV1").get("topN").asInt()).isEqualTo(10);
    }

    @Test
    void createTimestampedReportDirectoryCreatesFolder() {
        Path dir = writer.createTimestampedReportDirectory(tempDir, Instant.parse("2026-07-19T10:00:00Z"));
        assertThat(dir).exists();
        assertThat(dir.getFileName().toString()).matches("\\d{8}-\\d{6}");
    }

    private ClusterPairEvaluationReport clusterReport(
            String strategy, int total, int tp, int fp, int tn, int fn) {
        double precision = tp + fp == 0 ? Double.NaN : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? Double.NaN : (double) tp / (tp + fn);
        double f1 = (Double.isNaN(precision) || Double.isNaN(recall) || precision + recall == 0)
                ? Double.NaN : 2 * precision * recall / (precision + recall);
        double falseMerge = fp + tn == 0 ? Double.NaN : (double) fp / (fp + tn);
        double falseSplit = tp + fn == 0 ? Double.NaN : (double) fn / (tp + fn);
        return new ClusterPairEvaluationReport(
                strategy,
                Instant.parse("2026-07-19T10:00:00Z"),
                total, tp, fp, tn, fn,
                0, 0, 0,
                precision, recall, f1,
                falseMerge, falseSplit, 0.0,
                Map.of(),
                List.of()
        );
    }

    private RankingEvaluationReport rankingReport(
            String version, int topN, double precision, double ndcg) {
        return new RankingEvaluationReport(
                version, topN, Instant.parse("2026-07-19T10:00:00Z"),
                1, 3, 0,
                precision, ndcg, 0.1,
                1, 0, 0.0,
                null,
                List.of()
        );
    }
}
