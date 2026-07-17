package com.airadar.signal.vo;

import com.airadar.signal.model.GrowthConfidence;

public record GrowthMetricsVO(
    Long hotItemId,
    String window,
    Double attentionDelta,
    Double discussionDelta,
    Double adoptionDelta,
    Double relevanceDelta,
    Integer rankDelta,
    Double momentumScore,
    GrowthConfidence confidence
) {
}
