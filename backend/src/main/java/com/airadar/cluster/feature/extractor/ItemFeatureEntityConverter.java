package com.airadar.cluster.feature.extractor;

import com.airadar.cluster.feature.HotItemFeatureEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between the persisted {@link HotItemFeatureEntity} (JSONB columns)
 * and the typed in-memory {@link ItemFeature}.
 *
 * <p>Kept as a stateless utility so the V2 strategy can load a candidate's
 * feature vector without re-running the extractor pipeline.
 */
public final class ItemFeatureEntityConverter {

    private ItemFeatureEntityConverter() {
    }

    public static ItemFeature toFeature(HotItemFeatureEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ItemFeature(
                nullSafe(entity.getNormalizedTitle()),
                entity.getCanonicalUrl(),
                entity.getPublisherDomain(),
                entity.getEventTime(),
                externalIdsToMap(entity.getExternalIds()),
                entitiesToList(entity.getEntities()),
                keywordsToList(entity.getKeywords()),
                parseEventType(entity.getEventType())
        );
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> externalIdsToMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual()) {
                map.put(entry.getKey(), value.asText());
            }
        });
        return map;
    }

    private static List<EntityRef> entitiesToList(JsonNode node) {
        List<EntityRef> list = new ArrayList<>();
        if (!(node instanceof ArrayNode array)) {
            return list;
        }
        for (JsonNode element : array) {
            if (!element.isObject()) {
                continue;
            }
            String typeText = element.path("type").asText("PRODUCT");
            String value = element.path("value").asText("");
            String display = element.path("display").asText(value);
            if (value.isEmpty()) {
                continue;
            }
            EntityRef.Type type;
            try {
                type = EntityRef.Type.valueOf(typeText);
            } catch (IllegalArgumentException ex) {
                type = EntityRef.Type.PRODUCT;
            }
            list.add(new EntityRef(type, value, display));
        }
        return list;
    }

    private static List<String> keywordsToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (!(node instanceof ArrayNode array)) {
            return list;
        }
        for (JsonNode element : array) {
            if (element.isTextual()) {
                String text = element.asText();
                if (!text.isBlank()) {
                    list.add(text);
                }
            }
        }
        return list;
    }

    private static EventType parseEventType(String value) {
        if (value == null || value.isBlank()) {
            return EventType.UNKNOWN;
        }
        try {
            return EventType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return EventType.UNKNOWN;
        }
    }

    /**
     * Defensive accessor used when callers need a non-null event time.
     */
    public static Instant eventTimeOrNow(Instant value) {
        return value == null ? Instant.now() : value;
    }
}
