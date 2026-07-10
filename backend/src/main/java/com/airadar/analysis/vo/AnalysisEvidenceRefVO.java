package com.airadar.analysis.vo;

import com.airadar.source.model.SourceType;

public record AnalysisEvidenceRefVO(
        Long hotItemId,
        SourceType sourceType,
        String title,
        String sourceUrl
) {
}
