package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.source.model.SourceType;

import java.util.Map;

/**
 * Adapter for converting source-specific metrics into normalized signals.
 *
 * <p>Each adapter is responsible for interpreting the raw metrics from a specific
 * source type and translating them into the unified signal model.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Declare the supported source type via {@link #supportedType()}</li>
 *   <li>Handle null metrics gracefully (return zero/default signal)</li>
 *   <li>Preserve raw metrics in the normalized signal for traceability</li>
 *   <li>Normalize signal components to 0-100 range where applicable</li>
 * </ul>
 *
 * <p>Phase 18A adds an optional {@link #metricSemantics()} contract so the trend
 * layer can interpret per-field movement using source-specific rules instead of
 * a single coarse {@code METRIC_RESET} flag. Adapters that pre-date Phase 18A
 * inherit an empty map and continue to behave exactly as before.
 */
public interface SourceSignalAdapter {

    /**
     * Returns the source type this adapter supports.
     *
     * @return the supported source type
     */
    SourceType supportedType();

    /**
     * Converts a hot item's raw metrics into a normalized signal.
     *
     * @param hotItem the hot item with source-specific metrics
     * @return the normalized signal, or a zero/default signal if the item has no metrics
     */
    NormalizedSignal adapt(HotItemEntity hotItem);

    /**
     * Declares the {@link MetricSemantics} for each raw metric field this adapter
     * emits into {@link NormalizedSignal#rawMetrics()}.
     *
     * <p>The map key is the raw metric field name exactly as it appears in the
     * {@code metrics} JSON payload (for example {@code stargazersCount} or
     * {@code rank}). The trend layer uses the semantics to decide whether a
     * movement is expected (search rank drift), informational (relevance score),
     * volatile (social points), or anomalous (cumulative counter drop).
     *
     * <p>The default implementation returns an empty map, which preserves the
     * Phase 14 behavior for any adapter that has not yet declared semantics:
     * no raw metric deltas are produced for those fields.
     *
     * @return immutable map of raw metric field name to its semantics; never null
     */
    default Map<String, MetricSemantics> metricSemantics() {
        return Map.of();
    }
}
