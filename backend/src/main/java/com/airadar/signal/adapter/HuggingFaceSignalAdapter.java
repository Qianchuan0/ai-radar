package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Signal adapter for HuggingFace models.
 *
 * <p>Maps HuggingFace metrics to signal components:
 * <ul>
 *   <li>likes → attention</li>
 *   <li>downloads → adoption (primary adoption signal for models)</li>
 * </ul>
 *
 * <p>HuggingFace represents model ecosystem adoption through downloads and likes.
 * Download count is the strongest adoption signal, reflecting actual model usage.
 */
@Component
public class HuggingFaceSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_DOWNLOADS = 50000;
    private static final int MAX_LIKES = 500;

    private static final Map<String, MetricSemantics> METRIC_SEMANTICS = Map.of(
        // Downloads are strictly cumulative on the platform; a drop is a pipeline anomaly.
        "downloads", MetricSemantics.MONOTONIC_CUMULATIVE,
        // Likes rarely decrease but can be withdrawn; keep monotonic so an observed
        // drop is surfaced as an anomaly rather than silently swallowed.
        "likes", MetricSemantics.MONOTONIC_CUMULATIVE
    );

    @Override
    public SourceType supportedType() {
        return SourceType.HUGGING_FACE;
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

        int downloads = hotItem.getMetrics().path("downloads").asInt(0);
        int likes = hotItem.getMetrics().path("likes").asInt(0);

        // Attention: from likes
        double attention = normalizeLog(likes, MAX_LIKES);

        // Discussion: not applicable for HF models
        double discussion = 0.0;

        // Adoption: primary signal from downloads
        double adoption = normalizeLog(downloads, MAX_DOWNLOADS);

        return NormalizedSignal.of(
            SourceType.HUGGING_FACE,
            SourceRole.ADOPTION,
            attention,
            discussion,
            adoption,
            hotItem.getMetrics()
        );
    }

    private double normalizeLog(int value, int max) {
        int capped = Math.max(0, Math.min(value, max));
        return Math.log1p(capped) / Math.log1p(max) * 100.0;
    }

    private NormalizedSignal zeroSignal() {
        return NormalizedSignal.of(
            SourceType.HUGGING_FACE,
            SourceRole.ADOPTION,
            0.0,
            0.0,
            0.0,
            null
        );
    }
}
