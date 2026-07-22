package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WeiboHotSearchSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_HOT = 1_000_000;

    private static final Map<String, MetricSemantics> METRIC_SEMANTICS = Map.of(
        // Weibo raw_hot is a volatile buzz score that rises and falls with attention.
        "points", MetricSemantics.VOLATILE_SOCIAL,
        // Hot-search rank is recomputed every snapshot; movement is expected.
        "rank", MetricSemantics.RANK_LIKE_REVERSIBLE
    );

    @Override
    public SourceType supportedType() {
        return SourceType.WEIBO_HOT_SEARCH;
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
        int rank = hotItem.getMetrics().path("rank").asInt(0);
        double attention = normalizeLog(points, MAX_HOT);
        double discussion = rank > 0 ? Math.max(0.0, 100.0 - ((rank - 1) * 2.0)) : 0.0;
        return NormalizedSignal.of(
            SourceType.WEIBO_HOT_SEARCH,
            SourceRole.COMMUNITY,
            attention,
            discussion,
            0.0,
            hotItem.getMetrics()
        );
    }

    private NormalizedSignal zeroSignal() {
        return NormalizedSignal.of(SourceType.WEIBO_HOT_SEARCH, SourceRole.COMMUNITY, 0.0, 0.0, 0.0, null);
    }

    private double normalizeLog(int value, int max) {
        int capped = Math.max(0, Math.min(value, max));
        return Math.log1p(capped) / Math.log1p(max) * 100.0;
    }
}
