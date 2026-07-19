package com.airadar.cluster.governance.controller;

import com.airadar.cluster.governance.ClusterReviewService;
import com.airadar.cluster.governance.dto.ReviewResolutionRequest;
import com.airadar.cluster.governance.vo.ClusterReviewTaskVO;
import com.airadar.cluster.governance.vo.ReviewResolutionVO;
import com.airadar.common.api.ApiResponse;
import com.airadar.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cluster-review/tasks")
public class ClusterReviewController {

    private final ClusterReviewService reviewService;

    public ClusterReviewController(ClusterReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ClusterReviewTaskVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        return ApiResponse.success(reviewService.list(status, page, size));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ClusterReviewTaskVO> get(@PathVariable long taskId) {
        return ApiResponse.success(reviewService.get(taskId));
    }

    @PostMapping("/{taskId}/accept")
    public ApiResponse<ReviewResolutionVO> accept(
            @PathVariable long taskId,
            @Valid @RequestBody ReviewResolutionRequest request
    ) {
        return ApiResponse.success(reviewService.accept(taskId, request.reason(), request.operatorId()));
    }

    @PostMapping("/{taskId}/reject")
    public ApiResponse<ReviewResolutionVO> reject(
            @PathVariable long taskId,
            @Valid @RequestBody ReviewResolutionRequest request
    ) {
        return ApiResponse.success(reviewService.reject(taskId, request.reason(), request.operatorId()));
    }

    @PostMapping("/{taskId}/skip")
    public ApiResponse<ReviewResolutionVO> skip(
            @PathVariable long taskId,
            @Valid @RequestBody ReviewResolutionRequest request
    ) {
        return ApiResponse.success(reviewService.skip(taskId, request.reason(), request.operatorId()));
    }
}
