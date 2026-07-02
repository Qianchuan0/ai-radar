package com.airadar.cluster.vo;

import com.airadar.scoring.vo.HotScoreVO;

import java.time.Instant;
import java.util.List;

public record HotClusterDetailVO(
        Long id,
        String title,
        String summary,
        String status,
        Instant firstSeenAt,
        Instant lastSeenAt,
        int itemCount,
        HotScoreVO score,
        List<HotItemEvidenceVO> items
) {

    public HotClusterDetailVO {
        items = List.copyOf(items);
    }
}
