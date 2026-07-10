package com.airadar.analysis.client;

import com.airadar.analysis.vo.StructuredAnalysisResultVO;

public interface StructuredAnalysisModelClient {

    StructuredAnalysisResultVO analyze(ClusterEvidencePack evidencePack);
}
