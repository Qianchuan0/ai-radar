package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.source.model.SourceType;

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
}
