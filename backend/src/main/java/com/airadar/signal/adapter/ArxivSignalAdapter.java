package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ArxivSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_AUTHORS = 20;
    private static final int MAX_CATEGORIES = 10;

    private static final Map<String, MetricSemantics> METRIC_SEMANTICS = Map.of(
        // Authors and categories on a published paper do not shrink; any drop
        // indicates a parsing or pipeline regression rather than a real change.
        "authorsCount", MetricSemantics.MONOTONIC_CUMULATIVE,
        "categoriesCount", MetricSemantics.MONOTONIC_CUMULATIVE
    );

    @Override
    public SourceType supportedType() {
        return SourceType.ARXIV;
    }

    @Override
    public Map<String, MetricSemantics> metricSemantics() {
        return METRIC_SEMANTICS;
    }

    @Override
    public NormalizedSignal adapt(HotItemEntity hotItem) {
        if (hotItem.getMetrics() == null) {
            return zeroSignal();
        }
        int authorsCount = hotItem.getMetrics().path("authorsCount").asInt(0);
        int categoriesCount = hotItem.getMetrics().path("categoriesCount").asInt(0);
        double attention = 0.0;
        double discussion = 0.0;
        double adoption = normalize(authorsCount, MAX_AUTHORS) * 0.6 + normalize(categoriesCount, MAX_CATEGORIES) * 0.4;
        return new NormalizedSignal(
            SourceType.ARXIV,
            SourceRole.PRIMARY,
            attention,
            discussion,
            adoption,
            100.0,
            0.0,
            null,
            hotItem.getMetrics()
        );
    }

    private NormalizedSignal zeroSignal() {
        return new NormalizedSignal(SourceType.ARXIV, SourceRole.PRIMARY, 0.0, 0.0, 0.0, 100.0, 0.0, null, null);
    }

    private double normalize(int value, int max) {
        int capped = Math.max(0, Math.min(value, max));
        return (double) capped / max * 100.0;
    }
}
