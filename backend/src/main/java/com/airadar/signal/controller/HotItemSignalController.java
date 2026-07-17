package com.airadar.signal.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.signal.model.GrowthMetrics;
import com.airadar.signal.service.GrowthCalculationService;
import com.airadar.signal.service.SignalSnapshotService;
import com.airadar.signal.vo.GrowthMetricsVO;
import com.airadar.signal.vo.SignalSnapshotVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/hot-items")
public class HotItemSignalController {

    private final SignalSnapshotService signalSnapshotService;
    private final GrowthCalculationService growthCalculationService;

    public HotItemSignalController(
        SignalSnapshotService signalSnapshotService,
        GrowthCalculationService growthCalculationService
    ) {
        this.signalSnapshotService = signalSnapshotService;
        this.growthCalculationService = growthCalculationService;
    }

    @GetMapping("/{hotItemId}/signals")
    public ApiResponse<List<SignalSnapshotVO>> listSignals(
        @PathVariable long hotItemId,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.success(signalSnapshotService.listRecent(hotItemId, limit));
    }

    @GetMapping("/{hotItemId}/trend")
    public ApiResponse<GrowthMetricsVO> trend(
        @PathVariable long hotItemId,
        @RequestParam(defaultValue = "24h") String window
    ) {
        GrowthMetrics growthMetrics = growthCalculationService.calculate(hotItemId, window);
        return ApiResponse.success(toVO(growthMetrics));
    }

    private GrowthMetricsVO toVO(GrowthMetrics growthMetrics) {
        return new GrowthMetricsVO(
            growthMetrics.hotItemId(),
            growthMetrics.window(),
            growthMetrics.attentionDelta(),
            growthMetrics.discussionDelta(),
            growthMetrics.adoptionDelta(),
            growthMetrics.relevanceDelta(),
            growthMetrics.rankDelta(),
            growthMetrics.momentumScore(),
            growthMetrics.confidence()
        );
    }
}
