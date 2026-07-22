package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Signal adapter for HackerNews.
 *
 * <p>Maps HN metrics to signal components:
 * <ul>
 *   <li>points → attention</li>
 *   <li>commentsCount → discussion</li>
 *   <li>adoption → 0 (HN is not an adoption platform)</li>
 * </ul>
 *
 * <p>Signal normalization uses logarithmic scaling to match the V1 scoring approach,
 * where 500 points maps to 100% attention and 200 comments map to 100% discussion.
 */
@Component
public class HackerNewsSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_POINTS = 500;
    private static final int MAX_COMMENTS = 200;

    private static final Map<String, MetricSemantics> METRIC_SEMANTICS = Map.of(
        "points", MetricSemantics.VOLATILE_SOCIAL,
        "commentsCount", MetricSemantics.VOLATILE_SOCIAL
    );

    @Override
    public SourceType supportedType() {
        return SourceType.HACKER_NEWS;
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

        int points = hotItem.getMetrics().path("points").asInt(0);
        int commentsCount = hotItem.getMetrics().path("commentsCount").asInt(0);

        // Logarithmic normalization to 0-100
        double attention = normalizeLog(points, MAX_POINTS);
        double discussion = normalizeLog(commentsCount, MAX_COMMENTS);

        return NormalizedSignal.of(
            SourceType.HACKER_NEWS,
            SourceRole.COMMUNITY,
            attention,
            discussion,
            0.0,  // adoption
            hotItem.getMetrics()
        );
    }

    /**
     * Normalizes a value using logarithmic scaling.
     *
     * @param value the raw value
     * @param max   the cap value (values above are treated as max)
     * @return normalized score in 0-100 range
     */
    private double normalizeLog(int value, int max) {
        int capped = Math.max(0, Math.min(value, max));
        return Math.log1p(capped) / Math.log1p(max) * 100.0;
    }

    private NormalizedSignal zeroSignal() {
        return NormalizedSignal.of(
            SourceType.HACKER_NEWS,
            SourceRole.COMMUNITY,
            0.0,
            0.0,
            0.0,
            null
        );
    }
}
