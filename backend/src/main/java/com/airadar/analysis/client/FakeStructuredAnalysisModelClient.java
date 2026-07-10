package com.airadar.analysis.client;

import com.airadar.analysis.vo.AnalysisEvidenceRefVO;
import com.airadar.analysis.vo.StructuredAnalysisResultVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FakeStructuredAnalysisModelClient implements StructuredAnalysisModelClient {

    @Override
    public StructuredAnalysisResultVO analyze(ClusterEvidencePack evidencePack) {
        List<ClusterEvidencePack.EvidenceItemSnapshot> evidence = evidencePack.evidenceItems();
        List<AnalysisEvidenceRefVO> evidenceRefs = evidence.stream()
                .limit(3)
                .map(item -> new AnalysisEvidenceRefVO(
                        item.hotItemId(),
                        item.sourceType(),
                        item.title(),
                        item.sourceUrl()
                ))
                .toList();

        String normalizedTitle = compact(evidencePack.title());
        String brief = firstNonBlank(
                compact(evidencePack.summary()),
                evidence.stream().map(ClusterEvidencePack.EvidenceItemSnapshot::summary).map(this::compact).filter(this::hasText).findFirst().orElse(null),
                evidence.stream().map(ClusterEvidencePack.EvidenceItemSnapshot::title).map(this::compact).findFirst().orElse("No evidence summary available.")
        );
        String sourceSummary = evidencePack.sourceTypes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        String scoreText = evidencePack.totalScore() == null ? "no score yet" : "score " + evidencePack.totalScore().stripTrailingZeros().toPlainString();

        List<String> keySignals = new ArrayList<>();
        keySignals.add("The cluster currently aggregates " + evidence.size() + " evidence item(s).");
        keySignals.add("Source coverage includes " + sourceSummary + ".");
        keySignals.add("The cluster remains active with " + scoreText + ".");

        ClusterEvidencePack.EvidenceItemSnapshot latestEvidence = evidence.stream()
                .filter(item -> item.publishedAt() != null)
                .max(Comparator.comparing(ClusterEvidencePack.EvidenceItemSnapshot::publishedAt))
                .orElse(null);
        if (latestEvidence != null) {
            keySignals.add("Latest evidence came from " + latestEvidence.sourceType().name() + ".");
        }

        List<String> risks = new ArrayList<>();
        if (evidencePack.sourceTypes().size() <= 1) {
            risks.add("This signal still depends on a single source family.");
        }
        if (evidence.size() < 3) {
            risks.add("Evidence density is still light, so the narrative may shift after the next crawl.");
        }
        if (risks.isEmpty()) {
            risks.add("Cross-source clustering should still be spot-checked against raw evidence.");
        }

        List<String> followUps = List.of(
                "Compare the next crawl against this cluster to confirm the trend is still active.",
                "Review raw evidence for the top-linked source before escalating this signal."
        );

        String confidence;
        if (evidencePack.sourceTypes().size() >= 2 && evidence.size() >= 2) {
            confidence = "HIGH";
        } else if (evidence.size() >= 2) {
            confidence = "MEDIUM";
        } else {
            confidence = "LOW";
        }

        return new StructuredAnalysisResultVO(
                normalizedTitle,
                brief,
                buildWhyItMatters(evidencePack, sourceSummary),
                keySignals,
                evidenceRefs,
                risks,
                followUps,
                confidence
        );
    }

    private String buildWhyItMatters(ClusterEvidencePack evidencePack, String sourceSummary) {
        String scope = evidencePack.sourceTypes().size() > 1
                ? "multiple source types"
                : "a focused source channel";
        String timeWindow = evidencePack.lastSeenAt() != null ? "The latest evidence is still recent." : "The cluster already has a persisted evidence trail.";
        return "This cluster turns scattered signals into an event-level view across "
                + scope
                + " (" + sourceSummary + "). "
                + timeWindow;
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "No analysis summary available.";
    }
}
