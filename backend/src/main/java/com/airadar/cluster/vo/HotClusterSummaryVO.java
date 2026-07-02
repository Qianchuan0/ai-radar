package com.airadar.cluster.vo;

import com.airadar.scoring.vo.HotScoreVO;

import java.time.Instant;

public record HotClusterSummaryVO(
        Long id,
        String title,
        String summary,
        String status,
        Instant firstSeenAt,
        Instant lastSeenAt,
        int itemCount,
        HotScoreVO score
) {
}
