package com.airadar.scoring.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record HotScoreVO(
        BigDecimal total,
        String version,
        Instant calculatedAt,
        JsonNode components
) {
}
