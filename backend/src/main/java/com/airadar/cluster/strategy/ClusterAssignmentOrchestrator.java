package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.item.entity.HotItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordinates online + shadow clustering for a single hot item.
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
 */
@Service
public class ClusterAssignmentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ClusterAssignmentOrchestrator.class);

    private final CanonicalUrlClusterStrategy onlineStrategy;
    private final EventRuleClusterStrategy v2Strategy;
    private final ClusterStrategyProperties properties;

    public ClusterAssignmentOrchestrator(
            CanonicalUrlClusterStrategy onlineStrategy,
            EventRuleClusterStrategy v2Strategy,
            ClusterStrategyProperties properties
    ) {
        this.onlineStrategy = onlineStrategy;
        this.v2Strategy = v2Strategy;
        this.properties = properties;
    }

    /**
     * Assigns the item to a cluster using the online strategy, then runs
     * the shadow strategy if configured.
     *
     * @param item the hot item (must already be persisted)
     * @return the online strategy's assignment outcome
     */
    public ClusterAssignmentResult assign(HotItemEntity item) {
        ClusterAssignmentResult online = onlineStrategy.assign(item);
        HotClusterEntity cluster = online.getCluster();

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
}
