package com.airadar.alert.vo;

import java.time.Instant;

public record AlertMatchingRunVO(
        int scannedClusterCount,
        int matchedRuleCount,
        int createdAlertCount,
        int suppressedAlertCount,
        Instant completedAt
) {
}
