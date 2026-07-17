package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

@Component
public class HackerNewsSearchSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_POINTS = 500;
    private static final int MAX_COMMENTS = 200;

    @Override
    public SourceType supportedType() {
        return SourceType.HACKER_NEWS_SEARCH;
    }

    @Override
    public NormalizedSignal adapt(HotItemEntity hotItem) {
        if (hotItem.getMetrics() == null) {
            return zeroSignal();
        }
        int points = hotItem.getMetrics().path("points").asInt(0);
        int commentsCount = hotItem.getMetrics().path("commentsCount").asInt(0);
        return NormalizedSignal.of(
            SourceType.HACKER_NEWS_SEARCH,
            SourceRole.COMMUNITY,
            normalizeLog(points, MAX_POINTS),
            normalizeLog(commentsCount, MAX_COMMENTS),
            0.0,
            hotItem.getMetrics()
        );
    }

    private NormalizedSignal zeroSignal() {
        return NormalizedSignal.of(SourceType.HACKER_NEWS_SEARCH, SourceRole.COMMUNITY, 0.0, 0.0, 0.0, null);
    }

    private double normalizeLog(int value, int max) {
        int capped = Math.max(0, Math.min(value, max));
        return Math.log1p(capped) / Math.log1p(max) * 100.0;
    }
}
