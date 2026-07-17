package com.airadar.signal.vo;

import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record SignalSnapshotVO(
    Long id,
    Long hotItemId,
    Long rawItemId,
    SourceType sourceType,
    SourceRole sourceRole,
    Instant observedAt,
    JsonNode rawMetrics,
    JsonNode normalizedSignal,
    Instant createdAt
) {
}
