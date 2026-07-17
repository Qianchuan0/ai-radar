package com.airadar.signal.model;

public record GrowthMetrics(
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
