package com.airadar.scoring.strategy;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 18B scoring strategy configuration.
 *
 * <p>Bound to the {@code ai-radar.scoring.*} prefix. Supported configurations:
 *
 * <ol>
 *   <li><b>V1 online (default)</b>: {@code online-version=hn-score-v1}.
 *       Behaviour is identical to pre-Phase-18B — V1 drives list sorting,
 *       alerts, and daily reports; V2 is computed in shadow mode but never
 *       read by online consumers.</li>
 *   <li><b>V2 online + V1 fallback</b>: {@code online-version=cross-source-score-v2}.
 *       V2 drives list sorting, alerts, and daily reports. The
 *       {@code ScoringOrchestrator} still computes V1 on every crawl so the
 *       ranking layer can transparently fall back to V1 when a cluster has no
 *       V2 score row (e.g. older clusters scored before the rollout).</li>
 * </ol>
 *
 * <p>The V1 shadow score is always persisted alongside V2 by
 * {@code ScoringOrchestrator}, so flipping this property back to
 * {@code hn-score-v1} is enough to roll back — no data migration is needed.
 */
@ConfigurationProperties(prefix = "ai-radar.scoring")
public class ScoringStrategyProperties {

    /**
     * Default online version. Chosen conservatively so an operator who never
     * touches the scoring config keeps the pre-Phase-18B behaviour.
     */
    public static final String DEFAULT_ONLINE_VERSION = "hn-score-v1";

    private static final Set<String> ALLOWED_ONLINE_VERSIONS = new LinkedHashSet<>();
    static {
        ALLOWED_ONLINE_VERSIONS.add("hn-score-v1");
        ALLOWED_ONLINE_VERSIONS.add("cross-source-score-v2");
    }

    private String onlineVersion = DEFAULT_ONLINE_VERSION;

    public String getOnlineVersion() {
        return onlineVersion;
    }

    public void setOnlineVersion(String onlineVersion) {
        this.onlineVersion = onlineVersion;
    }

    /**
     * Returns true when the configured online version is the V2 score.
     */
    public boolean isV2Online() {
        return CrossSourceScoreV2Strategy.VERSION.equalsIgnoreCase(normalize(onlineVersion));
    }

    /**
     * Returns the effective online version, normalized to lower case for
     * safe comparisons against persisted {@code hot_score.scoring_version}
     * values. Never {@code null}.
     */
    public String effectiveOnlineVersion() {
        String normalized = normalize(onlineVersion);
        if (normalized == null || normalized.isBlank()) {
            return DEFAULT_ONLINE_VERSION;
        }
        return normalized;
    }

    @PostConstruct
    public void validate() {
        String normalized = normalize(onlineVersion);
        if (normalized == null || normalized.isBlank()) {
            onlineVersion = DEFAULT_ONLINE_VERSION;
            return;
        }
        if (!ALLOWED_ONLINE_VERSIONS.contains(normalized)) {
            throw new IllegalStateException(
                    "ai-radar.scoring.online-version only supports "
                            + ALLOWED_ONLINE_VERSIONS + " (got: " + onlineVersion + ").");
        }
        onlineVersion = normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
