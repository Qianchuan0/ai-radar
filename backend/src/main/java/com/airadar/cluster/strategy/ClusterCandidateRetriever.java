package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.feature.extractor.EntityRef;
import com.airadar.cluster.feature.extractor.ItemFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieves a bounded set of candidate clusters for a new {@link ItemFeature}.
 *
 * <p>The retriever implements every signal described in the Phase 16 plan:
 * <ul>
 *   <li><b>canonical URL</b> — exact match on {@code hot_item_feature.canonical_url}</li>
 *   <li><b>external id</b> — JSONB {@code ?|} existence on any shared id</li>
 *   <li><b>entity</b> — JSONB {@code @> containment} on shared canonical entity value</li>
 *   <li><b>keyword</b> — JSONB {@code ?|} existence on shared keywords</li>
 *   <li><b>time</b> — items within the configured window that did not match
 *       any of the above (kept as a fallback Level 3 input)</li>
 * </ul>
 *
 * <p>Hard limits (Phase 16 V1):
 * <ul>
 *   <li>Default time window is {@code 72h}. Callers can widen this for legacy
 *       events via {@link Options#windowHours}.</li>
 *   <li>Maximum of {@code 50} candidates per call.</li>
 *   <li>No full-table scan: every query includes the time-window predicate.</li>
 * </ul>
 */
@Component
public class ClusterCandidateRetriever {

    public static final int DEFAULT_WINDOW_HOURS = 72;
    public static final int DEFAULT_MAX_CANDIDATES = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ClusterCandidateRetriever(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<CandidateCluster> retrieve(ItemFeature feature) {
        return retrieve(feature, new Options(DEFAULT_WINDOW_HOURS, DEFAULT_MAX_CANDIDATES));
    }

    public List<CandidateCluster> retrieve(ItemFeature feature, Options options) {
        Instant referenceTime = feature.getEventTime() == null
                ? Instant.now()
                : feature.getEventTime();
        Instant windowStart = referenceTime.minusSeconds((long) options.windowHours * 3600L);
        long excludeItemId = -1L; // caller does not know its own hot_item_id yet

        Map<Long, CandidateCluster.Signal> signalsByItemId = new LinkedHashMap<>();

        addCanonicalUrlCandidates(feature, windowStart, excludeItemId, signalsByItemId);
        addExternalIdCandidates(feature, windowStart, excludeItemId, signalsByItemId);
        addEntityCandidates(feature, windowStart, excludeItemId, signalsByItemId);
        addKeywordCandidates(feature, windowStart, excludeItemId, signalsByItemId);
        addTimeFallbackCandidates(feature, windowStart, excludeItemId, signalsByItemId, options);

        // Resolve hot_item_id -> active cluster_id and cap to maxCandidates.
        Map<Long, Long> clusterByItem = resolveClusters(signalsByItemId.keySet());
        List<CandidateCluster> candidates = new ArrayList<>();
        for (Map.Entry<Long, CandidateCluster.Signal> entry : signalsByItemId.entrySet()) {
            Long itemId = entry.getKey();
            Long clusterId = clusterByItem.get(itemId);
            if (clusterId == null) {
                // Item has no active membership (orphan feature row). Skip.
                continue;
            }
            candidates.add(new CandidateCluster(itemId, clusterId, entry.getValue()));
            if (candidates.size() >= options.maxCandidates) {
                break;
            }
        }
        return candidates;
    }

    private void addCanonicalUrlCandidates(
            ItemFeature feature, Instant windowStart, long excludeItemId,
            Map<Long, CandidateCluster.Signal> signalsByItemId
    ) {
        String canonicalUrl = feature.getCanonicalUrl();
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            return;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT hot_item_id FROM hot_item_feature "
                        + "WHERE canonical_url = ? AND hot_item_id <> ? "
                        + "AND event_time IS NOT NULL AND event_time >= ?",
                Long.class,
                canonicalUrl, excludeItemId, windowStart
        );
        for (Long id : ids) {
            signalsByItemId.putIfAbsent(id, CandidateCluster.Signal.CANONICAL_URL);
        }
    }

    private void addExternalIdCandidates(
            ItemFeature feature, Instant windowStart, long excludeItemId,
            Map<Long, CandidateCluster.Signal> signalsByItemId
    ) {
        if (feature.getExternalIds().isEmpty()) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        feature.getExternalIds().forEach(payload::put);
        // JSONB ?| operator expects a text array of keys present in the object.
        List<String> keys = new ArrayList<>(feature.getExternalIds().keySet());
        String[] keyArray = keys.toArray(new String[0]);

        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT hot_item_id FROM hot_item_feature "
                        + "WHERE external_ids ?| ? "
                        + "AND hot_item_id <> ? "
                        + "AND event_time IS NOT NULL AND event_time >= ?",
                Long.class,
                createSqlTextArray(keyArray), excludeItemId, windowStart
        );
        for (Long id : ids) {
            signalsByItemId.putIfAbsent(id, CandidateCluster.Signal.EXTERNAL_ID);
        }
    }

    private void addEntityCandidates(
            ItemFeature feature, Instant windowStart, long excludeItemId,
            Map<Long, CandidateCluster.Signal> signalsByItemId
    ) {
        if (feature.getEntities().isEmpty()) {
            return;
        }
        // Look up features whose entities array contains any of our canonical
        // values. We use @> with a single-element array per query to keep the
        // SQL simple; in practice the entity set is small (1-4 entries).
        for (EntityRef ref : feature.getEntities()) {
            ObjectNode needle = objectMapper.createObjectNode();
            needle.put("value", ref.getValue());
            ArrayNode array = objectMapper.createArrayNode();
            array.add(needle);

            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT hot_item_id FROM hot_item_feature "
                            + "WHERE entities @> ?::jsonb "
                            + "AND hot_item_id <> ? "
                            + "AND event_time IS NOT NULL AND event_time >= ?",
                    Long.class,
                    array.toString(), excludeItemId, windowStart
            );
            for (Long id : ids) {
                signalsByItemId.putIfAbsent(id, CandidateCluster.Signal.ENTITY);
            }
        }
    }

    private void addKeywordCandidates(
            ItemFeature feature, Instant windowStart, long excludeItemId,
            Map<Long, CandidateCluster.Signal> signalsByItemId
    ) {
        if (feature.getKeywords().isEmpty()) {
            return;
        }
        String[] keywordArray = feature.getKeywords().toArray(new String[0]);
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT hot_item_id FROM hot_item_feature "
                        + "WHERE keywords ?| ? "
                        + "AND hot_item_id <> ? "
                        + "AND event_time IS NOT NULL AND event_time >= ?",
                Long.class,
                createSqlTextArray(keywordArray), excludeItemId, windowStart
        );
        for (Long id : ids) {
            signalsByItemId.putIfAbsent(id, CandidateCluster.Signal.KEYWORD);
        }
    }

    private void addTimeFallbackCandidates(
            ItemFeature feature, Instant windowStart, long excludeItemId,
            Map<Long, CandidateCluster.Signal> signalsByItemId,
            Options options
    ) {
        if (!feature.getEventType().name().equals("UNKNOWN") && !feature.getEntities().isEmpty()) {
            // Only fall back to "anything recent with same event type" when we
            // actually have an event type to match. This keeps the fallback
            // narrow.
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT hot_item_id FROM hot_item_feature "
                            + "WHERE event_type = ? "
                            + "AND hot_item_id <> ? "
                            + "AND event_time IS NOT NULL AND event_time >= ? "
                            + "LIMIT ?",
                    Long.class,
                    feature.getEventType().name(), excludeItemId, windowStart,
                    options.maxCandidates
            );
            for (Long id : ids) {
                signalsByItemId.putIfAbsent(id, CandidateCluster.Signal.TIME);
            }
        }
    }

    private Map<Long, Long> resolveClusters(Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        List<HotClusterItemEntity> memberships = selectActiveMemberships(itemIds);
        for (HotClusterItemEntity membership : memberships) {
            result.putIfAbsent(membership.getHotItemId(), membership.getHotClusterId());
        }
        return result;
    }

    private List<HotClusterItemEntity> selectActiveMemberships(Set<Long> itemIds) {
        // Query via JdbcTemplate to keep this component independent of the
        // V1 RuleBasedClusterService mapper wiring.
        return jdbcTemplate.query(
                "SELECT id, hot_cluster_id, hot_item_id, match_method, match_score, "
                        + "match_reason, rule_version, is_primary, assigned_at, removed_at "
                        + "FROM hot_cluster_item "
                        + "WHERE removed_at IS NULL AND hot_item_id IN "
                        + inPlaceholderList(itemIds.size()),
                (rs, rowNum) -> {
                    HotClusterItemEntity entity = new HotClusterItemEntity();
                    entity.setId(rs.getLong("id"));
                    entity.setHotClusterId(rs.getLong("hot_cluster_id"));
                    entity.setHotItemId(rs.getLong("hot_item_id"));
                    entity.setMatchMethod(rs.getString("match_method"));
                    entity.setMatchScore(rs.getBigDecimal("match_score"));
                    entity.setIsPrimary(rs.getBoolean("is_primary"));
                    entity.setAssignedAt(rs.getTimestamp("assigned_at").toInstant());
                    return entity;
                },
                itemIds.toArray()
        );
    }

    private static String inPlaceholderList(int count) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Build a PostgreSQL array literal suitable for the {@code ?|} operator.
     * Stored as a {@code String} so the JDBC driver hands it over as a plain
     * varchar, which PostgreSQL will cast to {@code text[]} for the operator.
     */
    private static String createSqlTextArray(String[] values) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values[i].replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    /**
     * Tunable retrieval knobs.
     */
    public static final class Options {

        private final int windowHours;
        private final int maxCandidates;

        public Options(int windowHours, int maxCandidates) {
            this.windowHours = Math.max(1, windowHours);
            this.maxCandidates = Math.max(1, maxCandidates);
        }

        public int getWindowHours() {
            return windowHours;
        }

        public int getMaxCandidates() {
            return maxCandidates;
        }
    }
}
