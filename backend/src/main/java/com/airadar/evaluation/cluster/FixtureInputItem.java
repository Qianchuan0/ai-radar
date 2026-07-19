package com.airadar.evaluation.cluster;

import com.airadar.source.model.SourceType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One in-memory input item inside a {@link ClusterBaselineFixture}.
 *
 * <p>The fixture item carries everything {@link ClusterEvaluationService}
 * needs to persist a real {@code hot_item} row (plus its backing
 * {@code raw_item}) so the strategy under test can run against the live
 * database just like the production crawl pipeline does.
 *
 * <p>The {@link #key} field is the fixture-local identifier used by
 * must-merge groups and must-not-merge pairs to reference this item. It is
 * intentionally decoupled from {@code hot_item.external_id} so fixture
 * authors can use stable, human-readable labels.
 */
public final class FixtureInputItem {

    private final String key;
    private final String externalId;
    private final SourceType sourceType;
    private final String itemType;
    private final String title;
    private final String summary;
    private final String sourceUrl;
    private final String author;
    private final List<String> tags;
    private final Map<String, Object> metrics;
    private final Instant publishedAt;

    private FixtureInputItem(Builder builder) {
        this.key = Objects.requireNonNull(builder.key, "key");
        this.externalId = Objects.requireNonNull(builder.externalId, "externalId");
        this.sourceType = Objects.requireNonNull(builder.sourceType, "sourceType");
        this.itemType = Objects.requireNonNull(builder.itemType, "itemType");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.summary = builder.summary;
        this.sourceUrl = Objects.requireNonNull(builder.sourceUrl, "sourceUrl");
        this.author = builder.author;
        this.tags = List.copyOf(builder.tags);
        this.metrics = Map.copyOf(builder.metrics);
        this.publishedAt = builder.publishedAt;
    }

    public String getKey() {
        return key;
    }

    public String getExternalId() {
        return externalId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getItemType() {
        return itemType;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getAuthor() {
        return author;
    }

    public List<String> getTags() {
        return tags;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String key;
        private String externalId;
        private SourceType sourceType;
        private String itemType;
        private String title;
        private String summary;
        private String sourceUrl;
        private String author;
        private List<String> tags = List.of();
        private Map<String, Object> metrics = Map.of();
        private Instant publishedAt;

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public Builder sourceType(SourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder itemType(String itemType) {
            this.itemType = itemType;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags == null ? List.of() : new ArrayList<>(tags);
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics == null ? Map.of() : new LinkedHashMap<>(metrics);
            return this;
        }

        public Builder publishedAt(Instant publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        public FixtureInputItem build() {
            return new FixtureInputItem(this);
        }
    }
}
