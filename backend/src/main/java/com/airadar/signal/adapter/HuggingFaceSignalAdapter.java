package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

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

    @Override
    public SourceType supportedType() {
        return SourceType.HUGGING_FACE;
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
