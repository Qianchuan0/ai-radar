package com.airadar.signal.model;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;

import java.time.Duration;
import java.util.Locale;

/**
 * Multi-window trend configuration supported by Phase 18A.
 *
 * <p>Each window defines its target look-back duration plus three tolerance
 * bands used to grade {@link GrowthConfidence}: the maximum allowed deviation
 * before a candidate historical snapshot is rejected, and the HIGH/MEDIUM
 * thresholds inside that band.
 *
 * <p>Windows are intentionally bounded to keep Phase 18A query cost
 * predictable; 7d/30d windows are deferred until a cluster trend cache table
 * is introduced.
 */
public enum TrendWindow {
    /** Short-term burst detection. Snapshot cadence must be dense for HIGH confidence. */
    H1(Duration.ofHours(1), Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ofMinutes(10)),
    /** Half-day trend. Balances recency against snapshot availability. */
    H6(Duration.ofHours(6), Duration.ofMinutes(60), Duration.ofMinutes(20), Duration.ofMinutes(40)),
    /** Phase 14 default window; preserved verbatim for backward compatibility. */
    H24(Duration.ofHours(24), Duration.ofHours(3), Duration.ofMinutes(30), Duration.ofMinutes(90)),
    /** Multi-day trend; looser tolerance because snapshots are sparse. */
    D3(Duration.ofHours(72), Duration.ofHours(12), Duration.ofHours(3), Duration.ofHours(6));

    private static final String WINDOW_24H_LEGACY = "24h";

    private final Duration target;
    private final Duration maxDeviation;
    private final Duration highConfidenceDeviation;
    private final Duration mediumConfidenceDeviation;

    TrendWindow(
        Duration target,
        Duration maxDeviation,
        Duration highConfidenceDeviation,
        Duration mediumConfidenceDeviation
    ) {
        this.target = target;
        this.maxDeviation = maxDeviation;
        this.highConfidenceDeviation = highConfidenceDeviation;
        this.mediumConfidenceDeviation = mediumConfidenceDeviation;
    }

    public Duration target() {
        return target;
    }

    public Duration maxDeviation() {
        return maxDeviation;
    }

    public Duration highConfidenceDeviation() {
        return highConfidenceDeviation;
    }

    public Duration mediumConfidenceDeviation() {
        return mediumConfidenceDeviation;
    }

    /**
     * Canonical wire code (e.g. {@code "1h"}, {@code "24h"}, {@code "3d"}).
     * Used by the API layer so callers do not need to know about the enum
     * names.
     *
     * <p>The {@code 24h} window keeps its Phase 14 wire form rather than being
     * converted to {@code 1d}, so existing API callers and persisted
     * {@code window} fields continue to match. Windows strictly longer than
     * 24h collapse to a day-count form (e.g. {@code 72h -> "3d"}).
     */
    public String code() {
        long hours = target.toHours();
        if (hours > 24 && hours % 24 == 0) {
            return (hours / 24) + "d";
        }
        return hours + "h";
    }

    /**
     * Parses a window code into a {@link TrendWindow}. Accepts the legacy
     * {@code "24h"} form as well as the canonical codes for every enum value.
     */
    public static TrendWindow parse(String window) {
        if (window == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Window must not be null.");
        }
        String normalized = window.trim().toLowerCase(Locale.ROOT);
        if (WINDOW_24H_LEGACY.equals(normalized)) {
            return H24;
        }
        for (TrendWindow value : values()) {
            if (value.code().equals(normalized)) {
                return value;
            }
        }
        throw new BusinessException(
            ErrorCode.INVALID_ARGUMENT,
            "Unsupported window '" + window + "'. Supported windows: 1h, 6h, 24h, 3d."
        );
    }
}
