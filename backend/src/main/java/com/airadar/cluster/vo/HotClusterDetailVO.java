package com.airadar.cluster.vo;

import com.airadar.scoring.vo.HotScoreVO;
import com.airadar.source.model.SourceType;

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
        List<SourceType> sourceTypes,
        HotScoreVO score,
        List<HotItemEvidenceVO> items
) {

    public HotClusterDetailVO {
        sourceTypes = List.copyOf(sourceTypes);
        items = List.copyOf(items);
    }
}
