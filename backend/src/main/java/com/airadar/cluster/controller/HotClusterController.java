package com.airadar.cluster.controller;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.cluster.vo.HotClusterSummaryVO;
import com.airadar.common.api.ApiResponse;
import com.airadar.common.api.PageResponse;
import com.airadar.source.model.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/v1/hot-clusters")
public class HotClusterController {

    private final HotClusterQueryService hotClusterQueryService;

    public HotClusterController(HotClusterQueryService hotClusterQueryService) {
        this.hotClusterQueryService = hotClusterQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<HotClusterSummaryVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "SCORE_DESC") HotClusterSort sort,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.success(hotClusterQueryService.list(page, size, sort, sourceType, from, to));
    }

    @GetMapping("/{clusterId}")
    public ApiResponse<HotClusterDetailVO> get(@PathVariable long clusterId) {
        return ApiResponse.success(hotClusterQueryService.get(clusterId));
    }
}
