package com.airadar.cluster.feature.extractor;

import org.springframework.stereotype.Component;

/**
 * Resolves the publisher domain for a hot item.
 *
 * <p>Phase 16 V1 only uses the registered host (without {@code www.}) as a
 * weak signal in Level 2 entity + organization matching. Source-specific
 * adapters already normalize most publishers via {@code HotItemEntity.author},
 * so this resolver is intentionally a thin host extractor.
 */
@Component
public class PublisherResolver {

    private final ExternalIdExtractor externalIdExtractor;

    public PublisherResolver(ExternalIdExtractor externalIdExtractor) {
        this.externalIdExtractor = externalIdExtractor;
    }

    public String resolve(String sourceUrl, String author) {
        String host = externalIdExtractor.hostOf(sourceUrl);
        if (host != null && !host.isBlank()) {
            return host;
        }
        return author;
    }
}
