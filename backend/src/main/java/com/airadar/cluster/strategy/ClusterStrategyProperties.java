package com.airadar.cluster.strategy;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 16 clustering strategy configuration.
 *
 * <p>Bound to the {@code ai-radar.cluster.*} prefix. Phase 16 supports
 * exactly two configurations:
 *
 * <ol>
 *   <li><b>V1 only</b> (default): {@code strategy=hn-rule-v1} and no
 *       {@code shadow-strategy} configured. Pipeline behavior is identical
 *       to pre-Phase-16.</li>
 *   <li><b>V1 online + V2 shadow</b>: {@code strategy=hn-rule-v1} and
 *       {@code shadow-strategy=event-rule-v2}. V1 still controls every
 *       {@code hot_cluster_item}; V2 runs evaluate-only and writes only
 *       {@code cluster_match_decision} rows for offline comparison.</li>
 * </ol>
 *
 * <p><b>Phase 16 contract:</b> {@code strategy} must remain
 * {@code hn-rule-v1}. Setting {@code strategy=event-rule-v2} is rejected at
 * startup — promoting V2 to the online strategy is Phase 17 work and
 * requires cluster merge/split governance to exist first. The
 * {@link ClusterAssignmentOrchestrator} enforces this by always delegating
 * online assignment to {@link CanonicalUrlClusterStrategy}.
 */
@ConfigurationProperties(prefix = "ai-radar.cluster")
public class ClusterStrategyProperties {

    private static final String ALLOWED_ONLINE_STRATEGY = "hn-rule-v1";
    private static final String ALLOWED_SHADOW_STRATEGY = "event-rule-v2";

    private String strategy = "hn-rule-v1";
    private String shadowStrategy;

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getShadowStrategy() {
        return shadowStrategy;
    }

    public void setShadowStrategy(String shadowStrategy) {
        this.shadowStrategy = shadowStrategy;
    }

    /**
     * Returns true when the shadow strategy is configured to a non-blank
     * value different from the online strategy.
     */
    public boolean isShadowEnabled() {
        return shadowStrategy != null
                && !shadowStrategy.isBlank()
                && !shadowStrategy.equals(strategy);
    }

    /**
     * Fails fast on unsupported Phase 16 configurations so operators do
     * not silently end up with V2-promoted-as-online or a no-op shadow.
     */
    @PostConstruct
    void validate() {
        if (strategy == null || !strategy.equals(ALLOWED_ONLINE_STRATEGY)) {
            throw new IllegalStateException(
                    "Phase 16 only supports ai-radar.cluster.strategy=" + ALLOWED_ONLINE_STRATEGY
                            + " (got: " + strategy + "). "
                            + "Promoting V2 to the online strategy is Phase 17 work."
            );
        }
        if (shadowStrategy != null && !shadowStrategy.isBlank()
                && !shadowStrategy.equals(ALLOWED_SHADOW_STRATEGY)) {
            throw new IllegalStateException(
                    "Phase 16 only supports ai-radar.cluster.shadow-strategy="
                            + ALLOWED_SHADOW_STRATEGY + " or empty (got: " + shadowStrategy + ")."
            );
        }
    }
}
