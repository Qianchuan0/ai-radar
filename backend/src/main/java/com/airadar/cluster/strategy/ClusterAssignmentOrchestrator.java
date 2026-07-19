package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.item.entity.HotItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Coordinates online + shadow + V2 online clustering for a single hot item.
 *
 * <p>This mirrors the Phase 15 {@code ScoringOrchestrator} pattern. The
 * online strategy (currently always {@link CanonicalUrlClusterStrategy},
 * i.e. {@code hn-rule-v1}) is authoritative: its returned cluster is what
 * every downstream component (scoring, analysis, alerts, reports) sees.
 *
 * <p>The shadow strategy (currently always {@link EventRuleClusterStrategy},
 * i.e. {@code event-rule-v2}) runs only when
 * {@code ai-radar.cluster.shadow-strategy} is configured. It never writes
 * {@code hot_cluster_item} — only {@code cluster_match_decision} — so the
 * online cluster state is untouched. Any exception is logged and swallowed
 * so a shadow-pipeline bug can never block the crawl loop.
 *
 * <p>Phase 17C adds an optional V2 <em>online</em> path. When
 * {@code ai-radar.cluster.v2-online.enabled=true} and the per-item traffic
 * + source gates clear, the orchestrator dispatches to
 * {@link V2OnlineAssignmentService}, which evaluates V2, picks the best
 * candidate, and — only when every level / confidence / target gate clears
 * — relocates the item from its V1 singleton into the V2 target cluster
 * through {@code MoveItemService}. V2 online runs inside a nested
 * savepoint, so any V2-side failure rolls back to the savepoint and leaves
 * V1's singleton intact. When V2 online is enabled, shadow evaluate is
 * skipped (the online path persists the same decision rows).
 */
@Service
public class ClusterAssignmentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClusterAssignmentOrchestrator.class);

    private final CanonicalUrlClusterStrategy onlineStrategy;
    private final EventRuleClusterStrategy v2Strategy;
    private final V2OnlineAssignmentService v2OnlineService;
    private final ClusterStrategyProperties properties;
    private final HotClusterMapper clusterMapper;

    public ClusterAssignmentOrchestrator(
            CanonicalUrlClusterStrategy onlineStrategy,
            EventRuleClusterStrategy v2Strategy,
            V2OnlineAssignmentService v2OnlineService,
            ClusterStrategyProperties properties,
            HotClusterMapper clusterMapper
    ) {
        this.onlineStrategy = onlineStrategy;
        this.v2Strategy = v2Strategy;
        this.v2OnlineService = v2OnlineService;
        this.properties = properties;
        this.clusterMapper = clusterMapper;
    }

    /**
     * Assigns the item to a cluster using the online strategy, then runs
     * the shadow strategy (or V2 online writer) if configured.
     *
     * @param item the hot item (must already be persisted)
     * @return the online strategy's assignment outcome; when V2 online
     *         moves the item, the returned cluster is the V2 target
     *         cluster rather than V1's singleton
     */
    public ClusterAssignmentResult assign(HotItemEntity item) {
        ClusterAssignmentResult online = onlineStrategy.assign(item);
        HotClusterEntity v1Cluster = online.getCluster();

        if (properties.isV2OnlineEnabled()) {
            return applyV2Online(item, online, v1Cluster);
        }

        if (properties.isShadowEnabled()) {
            try {
                v2Strategy.evaluate(item);
            } catch (RuntimeException ex) {
                log.warn("Shadow clustering strategy {} failed for item {}: {}",
                        properties.getShadowStrategy(), item.getId(), ex.toString());
            }
        }
        return online;
    }

    private ClusterAssignmentResult applyV2Online(HotItemEntity item,
                                                  ClusterAssignmentResult v1Result,
                                                  HotClusterEntity v1Cluster) {
        if (v1Cluster == null) {
            // Defensive: V1 always creates a cluster; if it did not, do not
            // attempt a V2 move because there is no source cluster id.
            return v1Result;
        }
        if (!passesTrafficGate(item)) {
            log.debug("V2 online: item {} skipped by traffic gate (percent={})",
                    item.getId(), properties.getV2Online().getTrafficPercent());
            return v1Result;
        }
        if (!passesSourceGate(item)) {
            log.debug("V2 online: item {} skipped by source allowlist", item.getId());
            return v1Result;
        }

        try {
            V2OnlineResult result = v2OnlineService.apply(item, v1Cluster.getId());
            if (result.movedToClusterId() == null) {
                log.debug("V2 online: item {} not moved ({})",
                        item.getId(), result.skipReason());
                return v1Result;
            }
            return overrideWithV2Target(v1Result, result);
        } catch (RuntimeException ex) {
            // NESTED propagation already rolled back to the savepoint.
            // V1's singleton is intact. Log loudly so operators notice.
            log.warn("V2 online assignment failed for item {}; staying with V1 cluster {}: {}",
                    item.getId(), v1Cluster.getId(), ex.toString());
            return v1Result;
        }
    }

    private ClusterAssignmentResult overrideWithV2Target(ClusterAssignmentResult v1Result,
                                                         V2OnlineResult result) {
        // The V1 singleton is now MERGED; downstream scoring/analysis must
        // see the V2 target cluster instead. Reload so version, status,
        // and primaryItemId reflect the post-move state.
        HotClusterEntity target = clusterMapper.selectById(result.movedToClusterId());
        if (target == null) {
            log.warn("V2 online moved item to cluster {} but it could not be reloaded; "
                    + "returning V1 result", result.movedToClusterId());
            return v1Result;
        }
        return ClusterAssignmentResult.builder()
                .cluster(target)
                .decision(AssignmentDecision.ACCEPTED)
                .matchMethod("V2_ONLINE_MOVE")
                .matchScore(v1Result.getMatchScore())
                .candidateClusterId(result.movedToClusterId())
                .matchReason(v1Result.getMatchReason())
                .ruleVersion(v1Result.getRuleVersion())
                .build();
    }

    private boolean passesTrafficGate(HotItemEntity item) {
        int percent = properties.getV2Online().getTrafficPercent();
        if (percent >= 100) {
            return true;
        }
        if (percent <= 0) {
            return false;
        }
        Long id = item.getId();
        if (id == null) {
            return false;
        }
        // Deterministic per-item gate so the same item always sees the same
        // outcome on replay. Math.floorMod keeps the bucket positive even
        // if the id ever went negative.
        int bucket = Math.floorMod(Objects.hashCode(id), 100);
        return bucket < percent;
    }

    private boolean passesSourceGate(HotItemEntity item) {
        Set<String> allowlist = properties.getV2Online().sourceAllowlistSet();
        if (allowlist.isEmpty()) {
            return true;
        }
        return item.getSourceType() != null && allowlist.contains(item.getSourceType().name());
    }

    /**
     * Returns the candidate-level decision rows the orchestrator would
     * surface for offline inspection. Used by the status API; production
     * callers go through {@link #assign(HotItemEntity)}.
     */
    public List<ClusterMatchDecisionEntity> shadowDecisionsFor(HotItemEntity item) {
        if (!properties.isShadowEnabled()) {
            return List.of();
        }
        try {
            return v2Strategy.evaluate(item);
        } catch (RuntimeException ex) {
            log.warn("Shadow evaluation failed for status inspection on item {}: {}",
                    item.getId(), ex.toString());
            return List.of();
        }
    }
}
