package com.airadar.report.vo;

import com.airadar.analysis.vo.ClusterAnalysisVO;
import com.airadar.scoring.vo.HotScoreVO;
import com.airadar.source.model.SourceType;

import java.time.Instant;
import java.util.List;

public record DailyReportClusterVO(
        Long hotClusterId,
        String title,
        String summary,
        List<SourceType> sourceTypes,
        int itemCount,
        HotScoreVO score,
        Instant firstSeenAt,
        Instant lastSeenAt,
        ClusterAnalysisVO latestAnalysis
) {

    public DailyReportClusterVO {
        sourceTypes = List.copyOf(sourceTypes);
    }
}
