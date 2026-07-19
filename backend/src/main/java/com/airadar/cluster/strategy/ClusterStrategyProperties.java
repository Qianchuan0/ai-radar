package com.airadar.cluster.strategy;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 16 / Phase 17C clustering strategy configuration.
 *
 * <p>Bound to the {@code ai-radar.cluster.*} prefix. Supported configurations:
 *
 * <ol>
 *   <li><b>V1 only</b> (default): {@code strategy=hn-rule-v1} and no
 *       {@code shadow-strategy} configured. Pipeline behavior is identical
 *       to pre-Phase-16.</li>
 *   <li><b>V1 online + V2 shadow</b>: {@code strategy=hn-rule-v1} and
 *       {@code shadow-strategy=event-rule-v2}. V1 still controls every
 *       {@code hot_cluster_item}; V2 runs evaluate-only and writes only
 *       {@code cluster_match_decision} rows for offline comparison.</li>
 *   <li><b>V1 online + V2 online (Phase 17C)</b>: {@code strategy=hn-rule-v1},
 *       optionally {@code shadow-strategy=event-rule-v2}, and
 *       {@code v2-online.enabled=true}. V1 still creates the initial
 *       singleton cluster for every item; when V2 finds an ACCEPTED candidate
 *       that clears the level / confidence / traffic gates, the orchestrator
 *       moves the item into the V2 target cluster through
 *       {@code MoveItemService}. V2 failures only roll back to a savepoint
 *       so V1's online assignment is never lost.</li>
 * </ol>
 *
 * <p><b>Phase 17C contract:</b> {@code strategy} must remain
 * {@code hn-rule-v1}. Setting {@code strategy=event-rule-v2} is still
 * rejected at startup — V2 never becomes the <em>sole</em> online strategy.
 * V2 online writes are gated by the {@link V2Online} sub-config so a single
 * misconfiguration cannot silently flip every crawl to V2.
 */
@ConfigurationProperties(prefix = "ai-radar.cluster")
public class ClusterStrategyProperties {

    private static final String ALLOWED_ONLINE_STRATEGY = "hn-rule-v1";
    private static final String ALLOWED_SHADOW_STRATEGY = "event-rule-v2";

    private String strategy = "hn-rule-v1";
    private String shadowStrategy;
    private V2Online v2Online = new V2Online();

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

    public V2Online getV2Online() {
        return v2Online;
    }

    public void setV2Online(V2Online v2Online) {
        this.v2Online = v2Online;
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
     * Returns true when V2 online writes are explicitly enabled. Callers
     * still need to check the traffic / source gates per item before
     * actually dispatching to {@code V2OnlineAssignmentService}.
     */
    public boolean isV2OnlineEnabled() {
        return v2Online != null && v2Online.isEnabled();
    }

    /**
     * Fails fast on unsupported configurations so operators do not silently
     * end up with V2-promoted-as-online, a no-op shadow, or a V2 online
     * rollout that bypasses the level / confidence gates.
     */
    @PostConstruct
    void validate() {
        if (strategy == null || !strategy.equals(ALLOWED_ONLINE_STRATEGY)) {
            throw new IllegalStateException(
                    "Phase 17C only supports ai-radar.cluster.strategy=" + ALLOWED_ONLINE_STRATEGY
                            + " (got: " + strategy + "). "
                            + "V2 online writes are gated by ai-radar.cluster.v2-online.enabled."
            );
        }
        if (shadowStrategy != null && !shadowStrategy.isBlank()
                && !shadowStrategy.equals(ALLOWED_SHADOW_STRATEGY)) {
            throw new IllegalStateException(
                    "Phase 17C only supports ai-radar.cluster.shadow-strategy="
                            + ALLOWED_SHADOW_STRATEGY + " or empty (got: " + shadowStrategy + ")."
            );
        }
        if (v2Online == null) {
            v2Online = new V2Online();
        }
        v2Online.validate();
    }

    /**
     * Phase 17C V2 online rollout configuration.
     *
     * <p>Every field defaults to the most conservative value so an
     * accidental {@code enabled=true} without further tuning still refuses
     * to write any real membership (0% traffic, no allowed levels).
     */
    public static class V2Online {

        private static final Set<String> ALLOWED_LEVELS = Set.of("L1", "L2", "L3");

        private boolean enabled = false;
        private int trafficPercent = 0;
        private List<String> allowedMatchLevels = new ArrayList<>(List.of("L1"));
        private double l3MinScore = 0.85;
        private boolean reviewRequiredToQueue = true;
        private List<String> sourceAllowlist = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTrafficPercent() {
            return trafficPercent;
        }

        public void setTrafficPercent(int trafficPercent) {
            this.trafficPercent = trafficPercent;
        }

        public List<String> getAllowedMatchLevels() {
            return allowedMatchLevels;
        }

        public void setAllowedMatchLevels(List<String> allowedMatchLevels) {
            this.allowedMatchLevels = allowedMatchLevels == null
                    ? new ArrayList<>()
                    : new ArrayList<>(allowedMatchLevels);
        }

        public double getL3MinScore() {
            return l3MinScore;
        }

        public void setL3MinScore(double l3MinScore) {
            this.l3MinScore = l3MinScore;
        }

        public boolean isReviewRequiredToQueue() {
            return reviewRequiredToQueue;
        }

        public void setReviewRequiredToQueue(boolean reviewRequiredToQueue) {
            this.reviewRequiredToQueue = reviewRequiredToQueue;
        }

        public List<String> getSourceAllowlist() {
            return sourceAllowlist;
        }

        public void setSourceAllowlist(List<String> sourceAllowlist) {
            this.sourceAllowlist = sourceAllowlist == null
                    ? new ArrayList<>()
                    : new ArrayList<>(sourceAllowlist);
        }

        /**
         * Returns the allowed-level set as a deduplicated, upper-cased set
         * for efficient lookup by match layer.
         */
        public Set<String> allowedLevelSet() {
            Set<String> normalized = new LinkedHashSet<>();
            for (String raw : allowedMatchLevels) {
                if (raw != null && !raw.isBlank()) {
                    normalized.add(raw.trim().toUpperCase(Locale.ROOT));
                }
            }
            return normalized;
        }

        /**
         * Returns the source allowlist as a deduplicated, trimmed set.
         * Empty when no allowlist is configured (every source passes).
         */
        public Set<String> sourceAllowlistSet() {
            Set<String> normalized = new LinkedHashSet<>();
            for (String raw : sourceAllowlist) {
                if (raw != null && !raw.isBlank()) {
                    normalized.add(raw.trim());
                }
            }
            return normalized;
        }

        void validate() {
            if (trafficPercent < 0 || trafficPercent > 100) {
                throw new IllegalStateException(
                        "ai-radar.cluster.v2-online.traffic-percent must be between 0 and 100"
                                + " (got: " + trafficPercent + ").");
            }
            Set<String> normalized = allowedLevelSet();
            for (String level : normalized) {
                if (!ALLOWED_LEVELS.contains(level)) {
                    throw new IllegalStateException(
                            "ai-radar.cluster.v2-online.allowed-match-levels only supports "
                                    + ALLOWED_LEVELS + " (got: " + level + ").");
                }
            }
            if (l3MinScore < 0.60 || l3MinScore > 1.00) {
                throw new IllegalStateException(
                        "ai-radar.cluster.v2-online.l3-min-score must be between 0.60 and 1.00"
                                + " (got: " + l3MinScore + ").");
            }
            if (enabled && trafficPercent == 0) {
                throw new IllegalStateException(
                        "ai-radar.cluster.v2-online.enabled=true requires traffic-percent > 0"
                                + " (got: " + trafficPercent + "). Set traffic-percent=100 for"
                                + " full rollout or use shadow-strategy for evaluate-only.");
            }
            if (enabled && normalized.isEmpty()) {
                throw new IllegalStateException(
                        "ai-radar.cluster.v2-online.enabled=true requires at least one"
                                + " allowed-match-levels entry.");
            }
        }
    }
}
