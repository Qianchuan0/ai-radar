package com.airadar.cluster.governance.controller;

import com.airadar.cluster.governance.ClusterMergeService;
import com.airadar.cluster.governance.ClusterMembershipHistoryQueryService;
import com.airadar.cluster.governance.ClusterSplitService;
import com.airadar.cluster.governance.MoveItemService;
import com.airadar.cluster.governance.ReclusterService;
import com.airadar.cluster.governance.dto.ClusterMergeRequest;
import com.airadar.cluster.governance.dto.ClusterReclusterRequest;
import com.airadar.cluster.governance.dto.ClusterSplitRequest;
import com.airadar.cluster.governance.dto.MoveItemRequest;
import com.airadar.cluster.governance.vo.ClusterMergeResultVO;
import com.airadar.cluster.governance.vo.ClusterSplitResultVO;
import com.airadar.cluster.governance.vo.MembershipHistoryVO;
import com.airadar.cluster.governance.vo.MoveItemResultVO;
import com.airadar.cluster.governance.vo.ReclusterResultVO;
import com.airadar.common.api.ApiResponse;
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

import java.util.List;

/**
 * Phase 17B governance endpoints scoped under {@code /api/v1/hot-clusters}.
 */
@Validated
@RestController
@RequestMapping("/api/v1/hot-clusters")
public class ClusterGovernanceController {

    private static final int DEFAULT_HISTORY_LIMIT = 100;

    private final ClusterMergeService mergeService;
    private final ClusterSplitService splitService;
    private final MoveItemService moveItemService;
    private final ReclusterService reclusterService;
    private final ClusterMembershipHistoryQueryService historyService;

    public ClusterGovernanceController(
            ClusterMergeService mergeService,
            ClusterSplitService splitService,
            MoveItemService moveItemService,
            ReclusterService reclusterService,
            ClusterMembershipHistoryQueryService historyService
    ) {
        this.mergeService = mergeService;
        this.splitService = splitService;
        this.moveItemService = moveItemService;
        this.reclusterService = reclusterService;
        this.historyService = historyService;
    }

    @PostMapping("/{clusterId}/merge")
    public ApiResponse<ClusterMergeResultVO> merge(
            @PathVariable long clusterId,
            @Valid @RequestBody ClusterMergeRequest request
    ) {
        return ApiResponse.success(mergeService.merge(
                clusterId,
                request.loserClusterId(),
                request.reason(),
                request.operatorId()
        ));
    }

    @PostMapping("/{clusterId}/split")
    public ApiResponse<ClusterSplitResultVO> split(
            @PathVariable long clusterId,
            @Valid @RequestBody ClusterSplitRequest request
    ) {
        return ApiResponse.success(splitService.split(
                clusterId,
                request.itemIds(),
                request.targetClusterId(),
                request.reason(),
                request.operatorId()
        ));
    }

    @PostMapping("/{clusterId}/items/{itemId}/move")
    public ApiResponse<MoveItemResultVO> move(
            @PathVariable long clusterId,
            @PathVariable long itemId,
            @Valid @RequestBody MoveItemRequest request
    ) {
        return ApiResponse.success(moveItemService.move(
                clusterId,
                itemId,
                request.targetClusterId(),
                request.reason(),
                request.operatorId()
        ));
    }

    @PostMapping("/{clusterId}/recluster")
    public ApiResponse<ReclusterResultVO> recluster(
            @PathVariable long clusterId,
            @Valid @RequestBody ClusterReclusterRequest request
    ) {
        return ApiResponse.success(reclusterService.recluster(
                clusterId,
                request.itemIds(),
                request.reason(),
                request.operatorId()
        ));
    }

    @GetMapping("/{clusterId}/membership-history")
    public ApiResponse<List<MembershipHistoryVO>> history(
            @PathVariable long clusterId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        return ApiResponse.success(historyService.listForCluster(clusterId, limit));
    }
}
