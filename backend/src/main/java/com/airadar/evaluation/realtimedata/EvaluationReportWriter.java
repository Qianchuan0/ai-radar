package com.airadar.evaluation.realtimedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes Phase 17A real-data evaluation reports to disk as machine-readable
 * JSON and a human-readable Markdown summary.
 *
 * <p>Output layout under the supplied {@code outputDir}:
 * <pre>
 * outputDir/
 *   cluster-v1.json     — {@link ClusterPairEvaluationReport} for hn-rule-v1
 *   cluster-v2.json     — {@link ClusterPairEvaluationReport} for event-rule-v2 (may be absent)
 *   ranking-v1.json     — {@link RankingEvaluationReport} for hn-score-v1
 *   ranking-v2.json     — {@link RankingEvaluationReport} for cross-source-score-v2 (may be absent)
 *   summary.md          — V1 vs V2 side-by-side metrics
 * </pre>
 *
 * <p>JSON files are written only for non-null reports; the Markdown summary
 * always renders and substitutes "n/a" for missing entries.
 */
@Service
public class EvaluationReportWriter {

    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;

    public EvaluationReportWriter(ObjectMapper objectMapper) {
        // Register JSR-310 defensively so the writer works with any ObjectMapper
        // (e.g. bare constructors in tests). Spring Boot's auto-configured mapper
        // already has the module; this is idempotent in that case.
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Resolves {@code evaluation/reports/{UTC-timestamp}/} under the supplied
     * project root and ensures it exists. Use this for the {@code outputDir}
     * parameter of {@link #write}.
     */
    public Path createTimestampedReportDirectory(Path projectRoot, Instant evaluatedAt) {
        Path dir = projectRoot.resolve("evaluation/reports/" + STAMP_FORMAT.format(evaluatedAt));
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create report directory " + dir, ex);
        }
        return dir;
    }

    public WrittenReports write(
            ClusterPairEvaluationReport clusterV1,
            ClusterPairEvaluationReport clusterV2,
            RankingEvaluationReport rankingV1,
            RankingEvaluationReport rankingV2,
            Path outputDir
    ) {
        Objects.requireNonNull(outputDir, "outputDir");
        try {
            Files.createDirectories(outputDir);
            Path clusterV1Path = writeJsonIfPresent("cluster-v1.json", outputDir, clusterV1);
            Path clusterV2Path = writeJsonIfPresent("cluster-v2.json", outputDir, clusterV2);
            Path rankingV1Path = writeJsonIfPresent("ranking-v1.json", outputDir, rankingV1);
            Path rankingV2Path = writeJsonIfPresent("ranking-v2.json", outputDir, rankingV2);
            Path summaryPath = outputDir.resolve("summary.md");
            Files.writeString(summaryPath,
                    buildSummary(clusterV1, clusterV2, rankingV1, rankingV2),
                    StandardCharsets.UTF_8);
            return new WrittenReports(
                    outputDir,
                    clusterV1Path,
                    clusterV2Path,
                    rankingV1Path,
                    rankingV2Path,
                    summaryPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write reports to " + outputDir, ex);
        }
    }

    /**
     * Compact JSON view of the aggregate metrics, suitable for persisting into
     * {@code evaluation_run.metrics_payload}. Per-case detail and
     * per-window top-N lists are omitted; callers that need them read the
     * JSON files on disk.
     */
    public ObjectNode buildMetricsPayload(
            ClusterPairEvaluationReport clusterV1,
            ClusterPairEvaluationReport clusterV2,
            RankingEvaluationReport rankingV1,
            RankingEvaluationReport rankingV2
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        if (clusterV1 != null) {
            root.set("clusterV1", clusterSummary(clusterV1));
        }
        if (clusterV2 != null) {
            root.set("clusterV2", clusterSummary(clusterV2));
        }
        if (rankingV1 != null) {
            root.set("rankingV1", rankingSummary(rankingV1));
        }
        if (rankingV2 != null) {
            root.set("rankingV2", rankingSummary(rankingV2));
        }
        return root;
    }

    private Path writeJsonIfPresent(String filename, Path dir, Object report) throws IOException {
        if (report == null) {
            return null;
        }
        Path path = dir.resolve(filename);
        objectMapper.writeValue(path.toFile(), report);
        return path;
    }

    private String buildSummary(
            ClusterPairEvaluationReport clusterV1,
            ClusterPairEvaluationReport clusterV2,
            RankingEvaluationReport rankingV1,
            RankingEvaluationReport rankingV2
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Phase 17A Real-Data Evaluation Report\n\n");
        sb.append("Evaluated at: `")
                .append(Optional.ofNullable(firstEvaluatedAt(clusterV1, clusterV2, rankingV1, rankingV2))
                        .map(Instant::toString).orElse("n/a"))
                .append("`\n\n");

        sb.append("## Cluster Pair Evaluation\n\n");
        sb.append("| Metric | V1 (hn-rule-v1) | V2 (event-rule-v2) |\n");
        sb.append("| --- | ---: | ---: |\n");
        sb.append("| Total pairs | ").append(cell(clusterV1 != null ? clusterV1.getTotalPairs() : null))
                .append(" | ").append(cell(clusterV2 != null ? clusterV2.getTotalPairs() : null)).append(" |\n");
        sb.append("| Pairwise precision | ").append(pct(clusterV1 != null ? clusterV1.getPairwisePrecision() : null))
                .append(" | ").append(pct(clusterV2 != null ? clusterV2.getPairwisePrecision() : null)).append(" |\n");
        sb.append("| Pairwise recall | ").append(pct(clusterV1 != null ? clusterV1.getPairwiseRecall() : null))
                .append(" | ").append(pct(clusterV2 != null ? clusterV2.getPairwiseRecall() : null)).append(" |\n");
        sb.append("| Pairwise F1 | ").append(pct(clusterV1 != null ? clusterV1.getPairwiseF1() : null))
                .append(" | ").append(pct(clusterV2 != null ? clusterV2.getPairwiseF1() : null)).append(" |\n");
        sb.append("| False merge rate | ").append(pct(clusterV1 != null ? clusterV1.getFalseMergeRate() : null))
                .append(" | ").append(pct(clusterV2 != null ? clusterV2.getFalseMergeRate() : null)).append(" |\n");
        sb.append("| False split rate | ").append(pct(clusterV1 != null ? clusterV1.getFalseSplitRate() : null))
                .append(" | ").append(pct(clusterV2 != null ? clusterV2.getFalseSplitRate() : null)).append(" |\n");
        sb.append("| Review required rate | ").append(pct(clusterV1 != null ? clusterV1.getReviewRequiredRate() : null))
                .append(" | ").append(pct(clusterV2 != null ? clusterV2.getReviewRequiredRate() : null)).append(" |\n");
        sb.append("| TP / FP / TN / FN | ")
                .append(cellCounts(clusterV1)).append(" | ").append(cellCounts(clusterV2)).append(" |\n");
        sb.append("| Unresolved pairs | ").append(cell(clusterV1 != null ? clusterV1.getUnresolvedCount() : null))
                .append(" | ").append(cell(clusterV2 != null ? clusterV2.getUnresolvedCount() : null)).append(" |\n\n");

        sb.append("## Ranking Evaluation\n\n");
        int topN = rankingV1 != null ? rankingV1.getTopN() : (rankingV2 != null ? rankingV2.getTopN() : 10);
        sb.append("| Metric | V1 (hn-score-v1) | V2 (cross-source-score-v2) |\n");
        sb.append("| --- | ---: | ---: |\n");
        sb.append("| Top-N | ").append(topN).append(" | ").append(topN).append(" |\n");
        sb.append("| Precision@").append(topN).append(" | ")
                .append(pct(rankingV1 != null ? rankingV1.getPrecisionAtN() : null))
                .append(" | ").append(pct(rankingV2 != null ? rankingV2.getPrecisionAtN() : null)).append(" |\n");
        sb.append("| NDCG@").append(topN).append(" | ")
                .append(pct(rankingV1 != null ? rankingV1.getNdcgAtN() : null))
                .append(" | ").append(pct(rankingV2 != null ? rankingV2.getNdcgAtN() : null)).append(" |\n");
        sb.append("| Top-").append(topN).append(" noise rate | ")
                .append(pct(rankingV1 != null ? rankingV1.getTopNNoiseRate() : null))
                .append(" | ").append(pct(rankingV2 != null ? rankingV2.getTopNNoiseRate() : null)).append(" |\n");
        sb.append("| Major-event miss rate | ")
                .append(pct(rankingV1 != null ? rankingV1.getMajorEventMissRate() : null))
                .append(" | ").append(pct(rankingV2 != null ? rankingV2.getMajorEventMissRate() : null)).append(" |\n");
        sb.append("| Major-event missed/total | ")
                .append(cellMiss(rankingV1)).append(" | ").append(cellMiss(rankingV2)).append(" |\n");
        sb.append("| Top-").append(topN).append(" diff vs V1 | n/a | ")
                .append(rankingV2 != null && rankingV2.getRankingDiffVsV1TopN() != null
                        ? String.valueOf(rankingV2.getRankingDiffVsV1TopN()) : "n/a")
                .append(" |\n\n");

        sb.append("## Acceptance Gates (informational)\n\n");
        sb.append("These are the thresholds from `docs/new_plan/01-real-data-evaluation.md`.\n");
        sb.append("Meeting them is a precondition for V2 taking over online writes.\n\n");
        sb.append("- Cluster pairwise precision >= 90%\n");
        sb.append("- Cluster pairwise recall >= 75%\n");
        sb.append("- False merge rate <= 5%\n");
        sb.append("- V2 NDCG@").append(topN).append(" >= V1 NDCG@").append(topN).append("\n");
        sb.append("- V2 major-event miss rate <= V1\n");
        sb.append("- Zero major-event misses by human review\n\n");
        sb.append("This report does not claim gate status; the maintainer must read the values ");
        sb.append("above and decide.\n");
        return sb.toString();
    }

    private ObjectNode clusterSummary(ClusterPairEvaluationReport r) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("strategyVersion", r.getStrategyVersion());
        node.put("totalPairs", r.getTotalPairs());
        node.put("truePositives", r.getTruePositives());
        node.put("falsePositives", r.getFalsePositives());
        node.put("trueNegatives", r.getTrueNegatives());
        node.put("falseNegatives", r.getFalseNegatives());
        node.put("ambiguousCount", r.getAmbiguousCount());
        node.put("unresolvedCount", r.getUnresolvedCount());
        putFraction(node, "pairwisePrecision", r.getPairwisePrecision());
        putFraction(node, "pairwiseRecall", r.getPairwiseRecall());
        putFraction(node, "pairwiseF1", r.getPairwiseF1());
        putFraction(node, "falseMergeRate", r.getFalseMergeRate());
        putFraction(node, "falseSplitRate", r.getFalseSplitRate());
        putFraction(node, "reviewRequiredRate", r.getReviewRequiredRate());
        return node;
    }

    private ObjectNode rankingSummary(RankingEvaluationReport r) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("scoringVersion", r.getScoringVersion());
        node.put("topN", r.getTopN());
        node.put("windowCount", r.getWindowCount());
        node.put("labeledClusterCount", r.getLabeledClusterCount());
        node.put("missingScoreCount", r.getMissingScoreCount());
        putFraction(node, "precisionAtN", r.getPrecisionAtN());
        putFraction(node, "ndcgAtN", r.getNdcgAtN());
        putFraction(node, "topNNoiseRate", r.getTopNNoiseRate());
        node.put("majorEventTotal", r.getMajorEventTotal());
        node.put("majorEventMissed", r.getMajorEventMissed());
        putFraction(node, "majorEventMissRate", r.getMajorEventMissRate());
        if (r.getRankingDiffVsV1TopN() != null) {
            node.put("rankingDiffVsV1TopN", r.getRankingDiffVsV1TopN());
        }
        return node;
    }

    private static void putFraction(ObjectNode node, String field, double value) {
        if (Double.isNaN(value)) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static Instant firstEvaluatedAt(
            ClusterPairEvaluationReport clusterV1,
            ClusterPairEvaluationReport clusterV2,
            RankingEvaluationReport rankingV1,
            RankingEvaluationReport rankingV2
    ) {
        if (clusterV1 != null) {
            return clusterV1.getEvaluatedAt();
        }
        if (clusterV2 != null) {
            return clusterV2.getEvaluatedAt();
        }
        if (rankingV1 != null) {
            return rankingV1.getEvaluatedAt();
        }
        if (rankingV2 != null) {
            return rankingV2.getEvaluatedAt();
        }
        return null;
    }

    private static String cell(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }

    private static String cellCounts(ClusterPairEvaluationReport r) {
        if (r == null) {
            return "n/a";
        }
        return r.getTruePositives() + "/" + r.getFalsePositives() + "/"
                + r.getTrueNegatives() + "/" + r.getFalseNegatives();
    }

    private static String cellMiss(RankingEvaluationReport r) {
        if (r == null) {
            return "n/a";
        }
        return r.getMajorEventMissed() + "/" + r.getMajorEventTotal();
    }

    private static String pct(Double value) {
        if (value == null || Double.isNaN(value)) {
            return "n/a";
        }
        return String.format("%.4f", value);
    }

    /**
     * Paths of files written by {@link #write}.
     */
    public record WrittenReports(
            Path directory,
            Path clusterV1Json,
            Path clusterV2Json,
            Path rankingV1Json,
            Path rankingV2Json,
            Path summaryMarkdown
    ) {
    }
}
