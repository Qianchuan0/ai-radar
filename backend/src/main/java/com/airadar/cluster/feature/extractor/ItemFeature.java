package com.airadar.cluster.feature.extractor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory feature vector for a single hot item, produced by
 * {@link ItemFeatureExtractor}.
 *
 * <p>This is the V2 clustering pipeline's primary input. It is converted to
 * a {@link com.airadar.cluster.feature.HotItemFeatureEntity} for persistence,
 * but kept as a typed Java model while the layered matcher runs so rules can
 * be expressed in plain Java without re-parsing JSONB.
 *
 * <p><b>Field semantics:</b>
 * <ul>
 *   <li>{@code normalizedTitle} — lower-cased, punctuation-folded title used
 *       by Level 3 title similarity</li>
 *   <li>{@code canonicalUrl} — canonical URL after tracking-param stripping
 *       via {@code UrlCanonicalizer}</li>
 *   <li>{@code publisherDomain} — registered host derived from the URL</li>
 *   <li>{@code eventTime} — best-known event time, falling back to crawl
 *       time when the source has no publish time</li>
 *   <li>{@code externalIds} — typed external identifiers
 *       ({@code arxiv}, {@code github}, {@code hf_model}, {@code hn_item},
 *       {@code tweet}, etc.)</li>
 *   <li>{@code entities} — resolved entity references, canonicalized via
 *       {@code EntityAliasDictionary}</li>
 *   <li>{@code keywords} — lower-cased keywords after stop-word removal</li>
 *   <li>{@code eventType} — coarse event classification</li>
 * </ul>
 */
public final class ItemFeature {

    private final String normalizedTitle;
    private final String canonicalUrl;
    private final String publisherDomain;
    private final Instant eventTime;
    private final Map<String, String> externalIds;
    private final List<EntityRef> entities;
    private final List<String> keywords;
    private final EventType eventType;

    public ItemFeature(
            String normalizedTitle,
            String canonicalUrl,
            String publisherDomain,
            Instant eventTime,
            Map<String, String> externalIds,
            List<EntityRef> entities,
            List<String> keywords,
            EventType eventType
    ) {
        this.normalizedTitle = Objects.requireNonNull(normalizedTitle, "normalizedTitle");
        this.canonicalUrl = canonicalUrl;
        this.publisherDomain = publisherDomain;
        this.eventTime = eventTime;
        this.externalIds = new LinkedHashMap<>(Objects.requireNonNull(externalIds, "externalIds"));
        this.entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        this.keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        this.eventType = Objects.requireNonNull(eventType, "eventType");
    }

    public String getNormalizedTitle() {
        return normalizedTitle;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getPublisherDomain() {
        return publisherDomain;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public Map<String, String> getExternalIds() {
        return externalIds;
    }

    public List<EntityRef> getEntities() {
        return entities;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public EventType getEventType() {
        return eventType;
    }
}
