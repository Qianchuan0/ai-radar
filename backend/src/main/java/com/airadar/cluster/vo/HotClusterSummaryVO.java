package com.airadar.cluster.vo;

import com.airadar.scoring.vo.HotScoreVO;
import com.airadar.source.model.SourceType;

import java.time.Instant;
import java.util.List;

public record HotClusterSummaryVO(
        Long id,
        String title,
        String summary,
        String status,
        Instant firstSeenAt,
        Instant lastSeenAt,
        int itemCount,
        List<SourceType> sourceTypes,
        HotScoreVO score
) {
}
