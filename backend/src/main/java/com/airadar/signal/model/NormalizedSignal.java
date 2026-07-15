package com.airadar.signal.model;

import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/**
 * Normalized signal representation that translates source-specific metrics
 * into unified semantic components.
 *
 * <p>Signal components are normalized to 0-100 range for cross-source comparison.
 * Raw metrics are preserved for traceability and explainability.
 *
 * @param sourceType    the original source type
 * @param sourceRole    the semantic role of the source
 * @param attention     public attention signal (views, impressions, exposure)
 * @param discussion    community discussion signal (comments, replies, mentions)
 * @param adoption      adoption and usage signal (stars, downloads, installs, forks)
 * @param authority     authority and credibility signal (citations, official status)
 * @param relevance     query/content relevance signal (search ranking, matching score)
 * @param rank          original ranking position (if applicable, e.g., search results)
 * @param rawMetrics    complete original metrics for traceability
 */
public record NormalizedSignal(
    SourceType sourceType,
    SourceRole sourceRole,
    double attention,
    double discussion,
    double adoption,
    double authority,
    double relevance,
    Integer rank,
    JsonNode rawMetrics
) {
    /**
     * Creates a NormalizedSignal without ranking information.
     */
    public static NormalizedSignal of(
        SourceType sourceType,
        SourceRole sourceRole,
        double attention,
        double discussion,
        double adoption,
        JsonNode rawMetrics
    ) {
        return new NormalizedSignal(
            sourceType,
            sourceRole,
            attention,
            discussion,
            adoption,
            0.0,          // authority (not yet used)
            0.0,          // relevance (not yet used)
            null,         // rank (not applicable)
            rawMetrics
        );
    }

    /**
     * Creates a NormalizedSignal for search sources with ranking.
     */
    public static NormalizedSignal ofSearchResult(
        SourceType sourceType,
        SourceRole sourceRole,
        double relevance,
        int rank,
        JsonNode rawMetrics
    ) {
        return new NormalizedSignal(
            sourceType,
            sourceRole,
            0.0,          // attention (search sources don't contribute social heat)
            0.0,          // discussion (search sources don't contribute social heat)
            0.0,          // adoption (search sources don't contribute adoption)
            0.0,          // authority (not yet used)
            relevance,    // relevance from search ranking
            rank,
            rawMetrics
        );
    }

    /**
     * Returns the rank if present, or empty otherwise.
     */
    public Optional<Integer> getRank() {
        return Optional.ofNullable(rank);
    }

    /**
     * Returns true if this signal represents a search result.
     */
    public boolean isSearchResult() {
        return getRank().isPresent();
    }

    /**
     * Returns the total social signal (attention + discussion + adoption).
     * This excludes relevance, which is specific to search sources.
     */
    public double totalSocialSignal() {
        return attention + discussion + adoption;
    }
}
