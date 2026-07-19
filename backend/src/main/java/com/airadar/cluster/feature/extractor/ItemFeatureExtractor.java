package com.airadar.cluster.feature.extractor;

import com.airadar.cluster.feature.HotItemFeatureEntity;
import com.airadar.cluster.feature.HotItemFeatureMapper;
import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.entity.HotItemEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Phase 16 entry point for producing an {@link ItemFeature} from a
 * {@link HotItemEntity}.
 *
 * <p>The extractor wires the individual normalizers and resolvers into a
 * single pipeline:
 * <pre>
 *   hot_item -&gt; TitleNormalizer
 *            -&gt; ExternalIdExtractor
 *            -&gt; EntityExtractor (with EntityAliasDictionary)
 *            -&gt; KeywordExtractor
 *            -&gt; EventTimeResolver
 *            -&gt; PublisherResolver
 *            -&gt; EventTypeResolver
 *            -&gt; ItemFeature
 *            -&gt; hot_item_feature row (persisted, upsert by hot_item_id)
 * </pre>
 *
 * <p>The persisted row is for auditability and future replay: V2 matchers
 * consume the in-memory {@link ItemFeature} directly so they never pay the
 * JSON round-trip cost during a single assignment.
 */
@Service
public class ItemFeatureExtractor {

    public static final String FEATURE_VERSION = "phase16-v1";

    private final TitleNormalizer titleNormalizer;
    private final ExternalIdExtractor externalIdExtractor;
    private final EntityExtractor entityExtractor;
    private final KeywordExtractor keywordExtractor;
    private final EventTimeResolver eventTimeResolver;
    private final PublisherResolver publisherResolver;
    private final EventTypeResolver eventTypeResolver;
    private final UrlCanonicalizer urlCanonicalizer;
    private final HotItemFeatureMapper featureMapper;
    private final ObjectMapper objectMapper;

    public ItemFeatureExtractor(
            TitleNormalizer titleNormalizer,
            ExternalIdExtractor externalIdExtractor,
            EntityExtractor entityExtractor,
            KeywordExtractor keywordExtractor,
            EventTimeResolver eventTimeResolver,
            PublisherResolver publisherResolver,
            EventTypeResolver eventTypeResolver,
            UrlCanonicalizer urlCanonicalizer,
            HotItemFeatureMapper featureMapper,
            ObjectMapper objectMapper
    ) {
        this.titleNormalizer = titleNormalizer;
        this.externalIdExtractor = externalIdExtractor;
        this.entityExtractor = entityExtractor;
        this.keywordExtractor = keywordExtractor;
        this.eventTimeResolver = eventTimeResolver;
        this.publisherResolver = publisherResolver;
        this.eventTypeResolver = eventTypeResolver;
        this.urlCanonicalizer = urlCanonicalizer;
        this.featureMapper = featureMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts the feature vector from the hot item without touching the
     * database.
     */
    public ItemFeature extract(HotItemEntity item) {
        String normalizedTitle = titleNormalizer.normalize(item.getTitle());
        String canonicalUrl = urlCanonicalizer.canonicalize(item.getSourceUrl());
        String publisherDomain = publisherResolver.resolve(item.getSourceUrl(), item.getAuthor());
        Instant eventTime = eventTimeResolver.resolve(
                item.getPublishedAt(),
                item.getFirstSeenAt(),
                item.getLastSeenAt()
        );
        var externalIds = externalIdExtractor.extract(item.getSourceUrl(), item.getExternalId(),
                item.getTitle(), item.getSummary());
        var entities = entityExtractor.extract(item.getTitle(), item.getSummary());
        var keywords = keywordExtractor.extract(item.getTitle(), item.getSummary());
        EventType eventType = eventTypeResolver.resolve(normalizedTitle, item.getSummary());

        return new ItemFeature(
                normalizedTitle,
                canonicalUrl,
                publisherDomain,
                eventTime,
                externalIds,
                entities,
                keywords,
                eventType
        );
    }

    /**
     * Extracts the feature vector and upserts the corresponding
     * {@code hot_item_feature} row.
     *
     * <p>Upsert is keyed on {@code hot_item_id} (uniquely constrained by V8).
     * Re-extracting for an already-feature'd item replaces the row in place
     * and refreshes {@code updated_at}.
     */
    public ItemFeature extractAndPersist(HotItemEntity item) {
        ItemFeature feature = extract(item);
        persist(item.getId(), feature);
        return feature;
    }

    private void persist(Long hotItemId, ItemFeature feature) {
        Instant now = Instant.now();
        HotItemFeatureEntity existing = featureMapper.selectOne(
                new LambdaQueryWrapper<HotItemFeatureEntity>()
                        .eq(HotItemFeatureEntity::getHotItemId, hotItemId)
        );

        JsonNode externalIdsJson = toJson(feature.getExternalIds());
        JsonNode entitiesJson = entitiesToJson(feature.getEntities());
        JsonNode keywordsJson = toJson(feature.getKeywords());

        if (existing == null) {
            HotItemFeatureEntity entity = new HotItemFeatureEntity();
            entity.setHotItemId(hotItemId);
            entity.setNormalizedTitle(feature.getNormalizedTitle());
            entity.setCanonicalUrl(feature.getCanonicalUrl());
            entity.setPublisherDomain(feature.getPublisherDomain());
            entity.setEventTime(feature.getEventTime());
            entity.setExternalIds(externalIdsJson);
            entity.setEntities(entitiesJson);
            entity.setKeywords(keywordsJson);
            entity.setEventType(feature.getEventType().name());
            entity.setFeatureVersion(FEATURE_VERSION);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            featureMapper.insert(entity);
        } else {
            existing.setNormalizedTitle(feature.getNormalizedTitle());
            existing.setCanonicalUrl(feature.getCanonicalUrl());
            existing.setPublisherDomain(feature.getPublisherDomain());
            existing.setEventTime(feature.getEventTime());
            existing.setExternalIds(externalIdsJson);
            existing.setEntities(entitiesJson);
            existing.setKeywords(keywordsJson);
            existing.setEventType(feature.getEventType().name());
            existing.setFeatureVersion(FEATURE_VERSION);
            existing.setUpdatedAt(now);
            featureMapper.updateById(existing);
        }
    }

    private JsonNode toJson(java.util.Map<String, String> map) {
        ObjectNode node = objectMapper.createObjectNode();
        map.forEach(node::put);
        return node;
    }

    private JsonNode toJson(List<String> values) {
        ArrayNode node = objectMapper.createArrayNode();
        values.forEach(node::add);
        return node;
    }

    private JsonNode entitiesToJson(List<EntityRef> entities) {
        ArrayNode node = objectMapper.createArrayNode();
        for (EntityRef ref : entities) {
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("type", ref.getType().name());
            entry.put("value", ref.getValue());
            entry.put("display", ref.getDisplay());
            node.add(entry);
        }
        return node;
    }
}
