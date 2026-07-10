package com.airadar.analysis.vo;

import java.util.List;

public record StructuredAnalysisResultVO(
        String headline,
        String brief,
        String whyItMatters,
        List<String> keySignals,
        List<AnalysisEvidenceRefVO> evidenceRefs,
        List<String> risks,
        List<String> followUps,
        String confidence
) {

    public StructuredAnalysisResultVO {
        keySignals = List.copyOf(keySignals);
        evidenceRefs = List.copyOf(evidenceRefs);
        risks = List.copyOf(risks);
        followUps = List.copyOf(followUps);
    }
}
