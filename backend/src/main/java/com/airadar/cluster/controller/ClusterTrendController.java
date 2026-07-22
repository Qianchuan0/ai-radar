package com.airadar.cluster.controller;

import com.airadar.cluster.model.ClusterTrend;
import com.airadar.cluster.service.ClusterTrendService;
import com.airadar.cluster.vo.ClusterTrendVO;
import com.airadar.common.api.ApiResponse;
import com.airadar.signal.model.RawMetricDelta;
import com.airadar.signal.model.TrendWindow;
import com.airadar.signal.vo.RawMetricDeltaVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Phase 18A cluster-level trend API.
 *
 * <p>Exposes aggregated trend metrics for a cluster across one or more windows.
 * The underlying {@link ClusterTrendService} computes trends live; no cache
 * table is persisted in the first version.
 */
@Validated
@RestController
@RequestMapping("/api/v1/hot-clusters")
public class ClusterTrendController {

    private static final String DEFAULT_WINDOWS = "1h,6h,24h,3d";

    private final ClusterTrendService clusterTrendService;

    public ClusterTrendController(ClusterTrendService clusterTrendService) {
        this.clusterTrendService = clusterTrendService;
    }

    /**
     * Returns cluster trend snapshots for each requested window.
     *
     * <p>The {@code windows} query parameter accepts a comma-separated list of
     * canonical codes ({@code 1h}, {@code 6h}, {@code 24h}, {@code 3d}). The
     * legacy {@code 24h} form is also accepted. Windows are returned in the
     * order they were requested.
     *
     * @param clusterId target cluster id
     * @param windows   comma-separated window codes; defaults to all four windows
     * @return one {@link ClusterTrendVO} per requested window
     */
    @GetMapping("/{clusterId}/trends")
    public ApiResponse<List<ClusterTrendVO>> trends(
        @PathVariable long clusterId,
        @RequestParam(defaultValue = DEFAULT_WINDOWS) String windows
    ) {
        List<TrendWindow> parsed = Arrays.stream(windows.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(TrendWindow::parse)
            .toList();
        List<ClusterTrendVO> results = parsed.stream()
            .map(window -> clusterTrendService.aggregate(clusterId, window))
            .map(this::toVO)
            .toList();
        return ApiResponse.success(results);
    }

    private ClusterTrendVO toVO(ClusterTrend trend) {
        return new ClusterTrendVO(
            trend.hotClusterId(),
            trend.window(),
            trend.trendState(),
            trend.momentumScore(),
            trend.confidence(),
            trend.rawMetricDeltas().stream().map(this::toVO).toList(),
            trend.normalizedDeltas(),
            trend.growthRate(),
            trend.acceleration(),
            trend.contributingItems(),
            trend.skippedItems(),
            trend.calculatedAt()
        );
    }

    private RawMetricDeltaVO toVO(RawMetricDelta delta) {
        return new RawMetricDeltaVO(
            delta.metric(),
            delta.previous(),
            delta.current(),
            delta.delta(),
            delta.growthRate(),
            delta.semantics(),
            delta.anomaly()
        );
    }
}
