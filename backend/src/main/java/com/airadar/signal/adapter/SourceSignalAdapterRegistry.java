package com.airadar.signal.adapter;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for source signal adapters.
 *
 * <p>Follows the same pattern as {@code CollectorRegistry} and {@code HotItemNormalizerRegistry}:
 * <ul>
 *   <li>Uses EnumMap for efficient type-based lookup</li>
 *   <li>Detects duplicate adapter registration at startup</li>
 *   <li>Throws clear business exception for missing adapters</li>
 *   <li>Creates immutable copy for thread-safe runtime access</li>
 * </ul>
 */
@Component
public class SourceSignalAdapterRegistry {

    private final Map<SourceType, SourceSignalAdapter> adapters;

    /**
     * Constructs the registry with a list of adapters.
     *
     * <p>Auto-injected by Spring from all {@link Component}-annotated adapter implementations.
     * Detects duplicate registration and throws IllegalStateException on startup.
     *
     * @param adapterList list of all registered adapters
     * @throws IllegalStateException if duplicate adapters for the same source type are found
     */
    public SourceSignalAdapterRegistry(List<SourceSignalAdapter> adapterList) {
        Map<SourceType, SourceSignalAdapter> registered = new EnumMap<>(SourceType.class);
        for (SourceSignalAdapter adapter : adapterList) {
            SourceSignalAdapter previous = registered.put(adapter.supportedType(), adapter);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate signal adapter for " + adapter.supportedType() +
                    ": " + previous.getClass().getSimpleName() + " vs " + adapter.getClass().getSimpleName()
                );
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    /**
     * Gets the required adapter for the given source type.
     *
     * @param sourceType the source type
     * @return the registered adapter
     * @throws BusinessException with {@link ErrorCode#SOURCE_TYPE_UNSUPPORTED} if no adapter is found
     */
    public SourceSignalAdapter getRequired(SourceType sourceType) {
        SourceSignalAdapter adapter = adapters.get(sourceType);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.SOURCE_TYPE_UNSUPPORTED);
        }
        return adapter;
    }

    /**
     * Adapts a hot item's metrics into a normalized signal using the registered adapter.
     *
     * @param hotItem the hot item to adapt
     * @return the normalized signal
     * @throws BusinessException with {@link ErrorCode#SOURCE_TYPE_UNSUPPORTED} if no adapter is found
     */
    public NormalizedSignal adapt(HotItemEntity hotItem) {
        SourceType sourceType = hotItem.getSourceType();
        SourceSignalAdapter adapter = getRequired(sourceType);
        return adapter.adapt(hotItem);
    }

    /**
     * Returns the number of registered adapters.
     *
     * @return adapter count
     */
    public int size() {
        return adapters.size();
    }

    /**
     * Returns true if an adapter is registered for the given source type.
     *
     * @param sourceType the source type to check
     * @return true if adapter exists, false otherwise
     */
    public boolean hasAdapter(SourceType sourceType) {
        return adapters.containsKey(sourceType);
    }
}
