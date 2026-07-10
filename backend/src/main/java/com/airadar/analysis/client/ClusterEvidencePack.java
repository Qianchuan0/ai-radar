package com.airadar.analysis.client;

import com.airadar.source.model.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClusterEvidencePack(
        long clusterId,
        String title,
        String summary,
        BigDecimal totalScore,
        List<SourceType> sourceTypes,
        List<EvidenceItemSnapshot> evidenceItems,
        Instant firstSeenAt,
        Instant lastSeenAt
) {

    public ClusterEvidencePack {
        sourceTypes = List.copyOf(sourceTypes);
        evidenceItems = List.copyOf(evidenceItems);
    }

    public record EvidenceItemSnapshot(
            long hotItemId,
            SourceType sourceType,
            String title,
            String summary,
            String sourceUrl,
            String author,
            Instant publishedAt
    ) {
    }
}
