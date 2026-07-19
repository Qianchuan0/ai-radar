package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.feature.HotItemFeatureEntity;
import com.airadar.cluster.feature.HotItemFeatureMapper;
import com.airadar.cluster.feature.extractor.ItemFeature;
import com.airadar.cluster.feature.extractor.ItemFeatureEntityConverter;
import com.airadar.cluster.feature.extractor.ItemFeatureExtractor;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 16 event-level clustering strategy ({@code event-rule-v2}).
 *
 * <p>Pipeline:
 * <pre>
 *   hot_item
 *     -&gt; ItemFeatureExtractor#extractAndPersist
 *     -&gt; check existing active membership (idempotent)
 *     -&gt; ClusterCandidateRetriever#retrieve
 *     -&gt; for each candidate: LayeredMatcher#match + persist decision
 *     -&gt; pick best outcome
 *     -&gt; ACCEPTED     : add membership to candidate's cluster
 *        REVIEW_REQUIRED: create singleton + flag candidate for review
 *        REJECTED       : create singleton
 *        NO_CANDIDATE   : create singleton
 * </pre>
 *
 * <p>Every candidate that was considered — accepted, rejected, or
 * review-required — is persisted to {@code cluster_match_decision} so the
 * audit trail is complete and the future Phase 17 governance backend has
 * everything it needs in one table.
 */
@Component
public class EventRuleClusterStrategy implements ClusterAssignmentStrategy {

    public static final String RULE_VERSION = "event-rule-v2";

    private static final Logger log = LoggerFactory.getLogger(EventRuleClusterStrategy.class);

    private final ItemFeatureExtractor featureExtractor;
    private final ClusterCandidateRetriever retriever;
    private final LayeredMatcher matcher;
    private final HotClusterMapper clusterMapper;
    private final HotClusterItemMapper clusterItemMapper;
    private final ClusterMatchDecisionMapper decisionMapper;
    private final HotItemFeatureMapper featureMapper;
    private final HotItemMapper hotItemMapper;
    private final PrimaryItemSelector primaryItemSelector;
    private final ObjectMapper objectMapper;

    public EventRuleClusterStrategy(
            ItemFeatureExtractor featureExtractor,
            ClusterCandidateRetriever retriever,
            LayeredMatcher matcher,
            HotClusterMapper clusterMapper,
            HotClusterItemMapper clusterItemMapper,
            ClusterMatchDecisionMapper decisionMapper,
            HotItemFeatureMapper featureMapper,
            HotItemMapper hotItemMapper,
            PrimaryItemSelector primaryItemSelector,
            ObjectMapper objectMapper
    ) {
        this.featureExtractor = featureExtractor;
        this.retriever = retriever;
        this.matcher = matcher;
        this.clusterMapper = clusterMapper;
        this.clusterItemMapper = clusterItemMapper;
        this.decisionMapper = decisionMapper;
        this.featureMapper = featureMapper;
        this.hotItemMapper = hotItemMapper;
        this.primaryItemSelector = primaryItemSelector;
        this.objectMapper = objectMapper;
    }

    @Override
    public String version() {
        return RULE_VERSION;
    }

    /**
     * Shadow-mode evaluation: runs the full V2 pipeline (extract features,
     * retrieve candidates, layered match, persist decisions) but does NOT
     * create or modify any {@code hot_cluster} or {@code hot_cluster_item}
     * row.
     *
     * <p>This is the production-pipeline entry point when V2 is configured
     * as a shadow strategy. The online cluster membership continues to be
     * controlled by {@link CanonicalUrlClusterStrategy} / V1; the shadow
     * run only populates {@code cluster_match_decision} for offline
     * comparison and future governance.
     *
     * @return the persisted shadow decisions (one per considered candidate;
     *         empty list when no candidates were retrieved)
     */
    public List<ClusterMatchDecisionEntity> evaluate(HotItemEntity item) {
        ItemFeature feature = featureExtractor.extractAndPersist(item);
        List<CandidateCluster> candidates = retriever.retrieve(feature);
        List<ClusterMatchDecisionEntity> persisted = new ArrayList<>();
        if (candidates.isEmpty()) {
            ClusterMatchDecisionEntity entity = new ClusterMatchDecisionEntity();
            entity.setHotItemId(item.getId());
            entity.setDecision(AssignmentDecision.NO_CANDIDATE.name());
            entity.setMatchScore(BigDecimal.ONE);
            entity.setMatchMethod("NO_CANDIDATE");
            entity.setMatchReason(simpleReason("NO_CANDIDATE", "shadow: no candidates retrieved"));
            entity.setRuleVersion(RULE_VERSION);
            entity.setDecidedAt(Instant.now());
            decisionMapper.insert(entity);
            persisted.add(entity);
            return persisted;
        }
        for (CandidateCluster candidate : candidates) {
            ItemFeature candidateFeature = loadCandidateFeature(candidate.getHotItemId());
            if (candidateFeature == null) {
                continue;
            }
            MatchOutcome outcome = matcher.match(feature, candidateFeature);
            ClusterMatchDecisionEntity entity = new ClusterMatchDecisionEntity();
            entity.setHotItemId(item.getId());
            entity.setCandidateClusterId(candidate.getClusterId());
            entity.setDecision(outcome.getDecision().name());
            entity.setMatchScore(outcome.getScore());
            entity.setMatchMethod(outcome.getMethod());
            entity.setMatchReason(outcome.toReasonJson(objectMapper));
            entity.setRuleVersion(RULE_VERSION);
            entity.setDecidedAt(Instant.now());
            decisionMapper.insert(entity);
            persisted.add(entity);
        }
        return persisted;
    }

    @Override
    public ClusterAssignmentResult assign(HotItemEntity item) {
        ItemFeature feature = featureExtractor.extractAndPersist(item);

        HotClusterItemEntity existingMembership = findActiveMembership(item.getId());
        if (existingMembership != null) {
            HotClusterEntity existing = clusterMapper.selectById(existingMembership.getHotClusterId());
            if (existing != null && "ACTIVE".equals(existing.getStatus())) {
                // Idempotent re-assignment: keep the item in its current cluster.
                return ClusterAssignmentResult.builder()
                        .cluster(existing)
                        .decision(AssignmentDecision.ACCEPTED)
                        .matchMethod("EXISTING")
                        .matchScore(BigDecimal.ONE)
                        .matchReason(simpleReason("EXISTING", "item already has an active membership"))
                        .ruleVersion(RULE_VERSION)
                        .build();
            }
        }

        List<CandidateCluster> candidates = retriever.retrieve(feature);
        if (candidates.isEmpty()) {
            HotClusterEntity singleton = createSingletonCluster(item);
            return ClusterAssignmentResult.builder()
                    .cluster(singleton)
                    .decision(AssignmentDecision.NO_CANDIDATE)
                    .matchMethod("NO_CANDIDATE")
                    .matchScore(BigDecimal.ONE)
                    .matchReason(simpleReason("NO_CANDIDATE", "no candidates retrieved"))
                    .ruleVersion(RULE_VERSION)
                    .build();
        }

        Scored best = pickBestCandidate(item, feature, candidates);
        if (best == null) {
            // All candidates had no feature row (race with extractor); fall
            // back to singleton so the pipeline keeps moving.
            HotClusterEntity singleton = createSingletonCluster(item);
            return ClusterAssignmentResult.builder()
                    .cluster(singleton)
                    .decision(AssignmentDecision.REJECTED)
                    .matchMethod("NO_FEATURE")
                    .matchScore(BigDecimal.ZERO)
                    .matchReason(simpleReason("NO_FEATURE", "candidate feature rows missing"))
                    .ruleVersion(RULE_VERSION)
                    .build();
        }

        if (best.outcome.getDecision() == AssignmentDecision.ACCEPTED) {
            HotClusterEntity target = clusterMapper.selectById(best.candidate.getClusterId());
            if (target == null || !"ACTIVE".equals(target.getStatus())) {
                // Candidate's cluster disappeared between retrieval and
                // assignment. Create a singleton so we do not point at a
                // dead cluster.
                log.warn("Candidate cluster {} vanished during assignment; creating singleton for item {}",
                        best.candidate.getClusterId(), item.getId());
                HotClusterEntity singleton = createSingletonCluster(item);
                return ClusterAssignmentResult.builder()
                        .cluster(singleton)
                        .decision(AssignmentDecision.REJECTED)
                        .matchMethod(best.outcome.getMethod())
                        .matchScore(best.outcome.getScore())
                        .candidateClusterId(best.candidate.getClusterId())
                        .matchReason(reasonFromOutcome(best.outcome, "candidate cluster vanished"))
                        .ruleVersion(RULE_VERSION)
                        .build();
            }
            addMembership(target, item, best.outcome, false);
            touchCluster(target, item.getLastSeenAt());
            primaryItemSelector.reselect(target);
            return ClusterAssignmentResult.builder()
                    .cluster(target)
                    .decision(AssignmentDecision.ACCEPTED)
                    .matchMethod(best.outcome.getMethod())
                    .matchScore(best.outcome.getScore())
                    .candidateClusterId(best.candidate.getClusterId())
                    .matchReason(reasonFromOutcome(best.outcome, "merged via " + best.outcome.getMethod()))
                    .ruleVersion(RULE_VERSION)
                    .build();
        }

        // REJECTED or REVIEW_REQUIRED: create a singleton and persist the
        // candidate cluster id for review.
        HotClusterEntity singleton = createSingletonCluster(item);
        return ClusterAssignmentResult.builder()
                .cluster(singleton)
                .decision(best.outcome.getDecision())
                .matchMethod(best.outcome.getMethod())
                .matchScore(best.outcome.getScore())
                .candidateClusterId(best.candidate.getClusterId())
                .matchReason(reasonFromOutcome(best.outcome, "singleton created; best candidate "
                        + best.candidate.getClusterId() + " not accepted"))
                .ruleVersion(RULE_VERSION)
                .build();
    }

    private Scored pickBestCandidate(HotItemEntity item, ItemFeature feature, List<CandidateCluster> candidates) {
        Scored best = null;
        for (CandidateCluster candidate : candidates) {
            ItemFeature candidateFeature = loadCandidateFeature(candidate.getHotItemId());
            if (candidateFeature == null) {
                continue;
            }
            MatchOutcome outcome = matcher.match(feature, candidateFeature);
            persistDecision(item.getId(), candidate, outcome);
            Scored scored = new Scored(candidate, outcome);
            if (best == null || rankedHigher(scored, best)) {
                best = scored;
            }
        }
        return best;
    }

    private boolean rankedHigher(Scored candidate, Scored current) {
        int candRank = decisionRank(candidate.outcome.getDecision());
        int curRank = decisionRank(current.outcome.getDecision());
        if (candRank != curRank) {
            return candRank > curRank;
        }
        return candidate.outcome.getScore().compareTo(current.outcome.getScore()) > 0;
    }

    private static int decisionRank(AssignmentDecision decision) {
        return switch (decision) {
            case ACCEPTED -> 3;
            case REVIEW_REQUIRED -> 2;
            case REJECTED -> 1;
            case NO_CANDIDATE -> 0;
        };
    }

    private HotClusterItemEntity findActiveMembership(long itemId) {
        return clusterItemMapper.selectOne(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotItemId, itemId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
    }

    private ItemFeature loadCandidateFeature(Long hotItemId) {
        HotItemFeatureEntity entity = featureMapper.selectOne(
                new LambdaQueryWrapper<HotItemFeatureEntity>()
                        .eq(HotItemFeatureEntity::getHotItemId, hotItemId)
        );
        if (entity != null) {
            return ItemFeatureEntityConverter.toFeature(entity);
        }
        // Candidate has no feature row yet (e.g. it was created by V1 before
        // V2 was enabled). Back-fill on the fly so the candidate is not
        // silently skipped every cycle.
        HotItemEntity hotItem = hotItemMapper.selectById(hotItemId);
        if (hotItem == null) {
            return null;
        }
        return featureExtractor.extractAndPersist(hotItem);
    }

    private HotClusterEntity createSingletonCluster(HotItemEntity item) {
        Instant now = Instant.now();
        HotClusterEntity cluster = new HotClusterEntity();
        cluster.setTitle(item.getTitle());
        cluster.setSummary(item.getSummary());
        cluster.setStatus("ACTIVE");
        cluster.setPrimaryItemId(item.getId());
        cluster.setFirstSeenAt(item.getFirstSeenAt() == null ? now : item.getFirstSeenAt());
        cluster.setLastSeenAt(item.getLastSeenAt() == null ? now : item.getLastSeenAt());
        cluster.setVersion(0);
        cluster.setCreatedAt(now);
        cluster.setUpdatedAt(now);
        clusterMapper.insert(cluster);

        ObjectNode reason = objectMapper.createObjectNode();
        reason.put("method", "SINGLETON");
        reason.put("ruleVersion", RULE_VERSION);

        HotClusterItemEntity membership = new HotClusterItemEntity();
        membership.setHotClusterId(cluster.getId());
        membership.setHotItemId(item.getId());
        membership.setMatchMethod("SINGLETON");
        membership.setMatchScore(BigDecimal.ONE);
        membership.setMatchReason(reason);
        membership.setRuleVersion(RULE_VERSION);
        membership.setIsPrimary(true);
        membership.setAssignedAt(now);
        clusterItemMapper.insert(membership);
        return cluster;
    }

    private void addMembership(HotClusterEntity cluster, HotItemEntity item, MatchOutcome outcome, boolean primary) {
        HotClusterItemEntity membership = new HotClusterItemEntity();
        membership.setHotClusterId(cluster.getId());
        membership.setHotItemId(item.getId());
        membership.setMatchMethod(outcome.getMethod());
        membership.setMatchScore(outcome.getScore());
        membership.setMatchReason(reasonFromOutcome(outcome, "added by " + RULE_VERSION));
        membership.setRuleVersion(RULE_VERSION);
        membership.setIsPrimary(primary);
        membership.setAssignedAt(Instant.now());
        clusterItemMapper.insert(membership);
    }

    private void touchCluster(HotClusterEntity cluster, Instant lastSeenAt) {
        if (cluster == null) {
            return;
        }
        if (lastSeenAt != null && (cluster.getLastSeenAt() == null || lastSeenAt.isAfter(cluster.getLastSeenAt()))) {
            cluster.setLastSeenAt(lastSeenAt);
        }
        cluster.setUpdatedAt(Instant.now());
        cluster.setVersion(cluster.getVersion() == null ? 1 : cluster.getVersion() + 1);
        clusterMapper.updateById(cluster);
    }

    private void persistDecision(Long hotItemId, CandidateCluster candidate, MatchOutcome outcome) {
        ClusterMatchDecisionEntity entity = new ClusterMatchDecisionEntity();
        entity.setHotItemId(hotItemId);
        entity.setCandidateClusterId(candidate.getClusterId());
        entity.setDecision(outcome.getDecision().name());
        entity.setMatchScore(outcome.getScore());
        entity.setMatchMethod(outcome.getMethod());
        entity.setMatchReason(outcome.toReasonJson(objectMapper));
        entity.setRuleVersion(RULE_VERSION);
        entity.setDecidedAt(Instant.now());
        decisionMapper.insert(entity);
    }

    private JsonNode simpleReason(String method, String detail) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("method", method);
        node.put("detail", detail);
        node.put("ruleVersion", RULE_VERSION);
        return node;
    }

    private JsonNode reasonFromOutcome(MatchOutcome outcome, String detail) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("method", outcome.getMethod());
        node.put("layer", outcome.getLayer());
        node.put("score", outcome.getScore());
        node.put("detail", detail);
        node.set("components", objectMapper.valueToTree(outcome.getComponents()));
        return node;
    }

    private record Scored(CandidateCluster candidate, MatchOutcome outcome) {
    }
}
