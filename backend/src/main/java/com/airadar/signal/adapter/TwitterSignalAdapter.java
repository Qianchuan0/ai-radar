package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

@Component
public class TwitterSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_VIEWS = 100_000;
    private static final int MAX_DISCUSSION = 10_000;
    private static final int MAX_ADOPTION = 25_000;

    @Override
    public SourceType supportedType() {
        return SourceType.TWITTER;
    }

    @Override
    public NormalizedSignal adapt(HotItemEntity hotItem) {
        if (hotItem.getMetrics() == null) {
            return zeroSignal();
        }
        int likeCount = hotItem.getMetrics().path("likeCount").asInt(0);
        int retweetCount = hotItem.getMetrics().path("retweetCount").asInt(0);
        int replyCount = hotItem.getMetrics().path("replyCount").asInt(0);
        int quoteCount = hotItem.getMetrics().path("quoteCount").asInt(0);
        int viewCount = hotItem.getMetrics().path("viewCount").asInt(0);
        double attention = normalizeLog(viewCount + likeCount, MAX_VIEWS);
        double discussion = normalizeLog(replyCount + quoteCount, MAX_DISCUSSION);
        double adoption = normalizeLog(likeCount + (retweetCount * 3), MAX_ADOPTION);
        return NormalizedSignal.of(
            SourceType.TWITTER,
            SourceRole.COMMUNITY,
            attention,
            discussion,
            adoption,
            hotItem.getMetrics()
        );
    }

    private NormalizedSignal zeroSignal() {
        return NormalizedSignal.of(SourceType.TWITTER, SourceRole.COMMUNITY, 0.0, 0.0, 0.0, null);
    }

    private double normalizeLog(int value, int max) {
        int capped = Math.max(0, Math.min(value, max));
        return Math.log1p(capped) / Math.log1p(max) * 100.0;
    }
}
