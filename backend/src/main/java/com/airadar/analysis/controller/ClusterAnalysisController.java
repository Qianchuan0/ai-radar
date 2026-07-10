package com.airadar.analysis.controller;

import com.airadar.analysis.service.AnalysisService;
import com.airadar.analysis.vo.ClusterAnalysisVO;
import com.airadar.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hot-clusters")
public class ClusterAnalysisController {

    private final AnalysisService analysisService;

    public ClusterAnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/{clusterId}/analysis-runs")
    public ApiResponse<ClusterAnalysisVO> trigger(@PathVariable long clusterId) {
        return ApiResponse.success(analysisService.triggerLatest(clusterId));
    }

    @GetMapping("/{clusterId}/analysis")
    public ApiResponse<ClusterAnalysisVO> latest(@PathVariable long clusterId) {
        return ApiResponse.success(analysisService.getLatest(clusterId));
    }
}
