package com.airadar.cluster.controller;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.cluster.vo.HotClusterSummaryVO;
import com.airadar.common.api.ApiResponse;
import com.airadar.common.api.PageResponse;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.airadar.scoring.vo.HotClusterScoreVO;
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
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/hot-clusters")
public class HotClusterController {

    private final HotClusterQueryService hotClusterQueryService;
    private final HotScoreMapper hotScoreMapper;

    public HotClusterController(HotClusterQueryService hotClusterQueryService, HotScoreMapper hotScoreMapper) {
        this.hotClusterQueryService = hotClusterQueryService;
        this.hotScoreMapper = hotScoreMapper;
    }

    @GetMapping
    public ApiResponse<PageResponse<HotClusterSummaryVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "SCORE_DESC") HotClusterSort sort,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String scoringVersion
    ) {
        return ApiResponse.success(
                hotClusterQueryService.list(page, size, sort, sourceType, from, to, scoringVersion)
        );
    }

    @GetMapping("/{clusterId}")
    public ApiResponse<HotClusterDetailVO> get(
            @PathVariable long clusterId,
            @RequestParam(required = false) String scoringVersion
    ) {
        return ApiResponse.success(hotClusterQueryService.get(clusterId, scoringVersion));
    }

    /**
     * Returns every persisted score for a cluster, across all scoring versions.
     *
     * <p>Phase 15 shadow scoring writes both {@code hn-score-v1} and
     * {@code cross-source-score-v2}; this endpoint exposes them side by side
     * for comparison and offline analysis.
     *
     * @param clusterId the hot cluster id
     * @return score records ordered by calculation time descending
     */
    @GetMapping("/{clusterId}/scores")
    public ApiResponse<List<HotClusterScoreVO>> scores(@PathVariable long clusterId) {
        List<HotScoreEntity> entities = hotScoreMapper.selectByCluster(clusterId);
        List<HotClusterScoreVO> vos = entities.stream()
                .map(entity -> new HotClusterScoreVO(
                        entity.getScoringVersion(),
                        entity.getTotalScore(),
                        entity.getCalculatedAt(),
                        entity.getScoreComponents()))
                .toList();
        return ApiResponse.success(vos);
    }
}
