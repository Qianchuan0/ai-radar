package com.airadar.crawl.collector;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CollectorRegistry {

    private final Map<SourceType, SourceCollector> collectors;

    public CollectorRegistry(List<SourceCollector> collectorList) {
        Map<SourceType, SourceCollector> registered = new EnumMap<>(SourceType.class);
        for (SourceCollector collector : collectorList) {
            SourceCollector previous = registered.put(collector.supportedType(), collector);
            if (previous != null) {
                throw new IllegalStateException("Duplicate collector for " + collector.supportedType());
            }
        }
        this.collectors = Map.copyOf(registered);
    }

    public SourceCollector getRequired(SourceType sourceType) {
        SourceCollector collector = collectors.get(sourceType);
        if (collector == null) {
            throw new BusinessException(ErrorCode.SOURCE_TYPE_UNSUPPORTED);
        }
        return collector;
    }
}
