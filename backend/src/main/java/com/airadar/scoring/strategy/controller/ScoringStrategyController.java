package com.airadar.scoring.strategy.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.scoring.strategy.CrossSourceScoreV2Strategy;
import com.airadar.scoring.strategy.ScoringStrategyProperties;
import com.airadar.scoring.strategy.vo.ScoringStrategyStatusVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 18B status endpoint for the online scoring strategy.
 *
 * <p>Single endpoint {@code GET /api/v1/scoring-strategy/status} returns the
 * configured online scoring version, the V2 flag, and the human-readable
 * rollout stage. Intended for ops / debugging use during the gradual V2
 * rollout.
 */
@RestController
@RequestMapping("/api/v1/scoring-strategy")
public class ScoringStrategyController {

    private final ScoringStrategyProperties properties;

    public ScoringStrategyController(ScoringStrategyProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    public ApiResponse<ScoringStrategyStatusVO> status() {
        String onlineVersion = properties.effectiveOnlineVersion();
        boolean v2Online = properties.isV2Online();
        String stage = v2Online ? "V2_ONLINE_V1_SHADOW" : "V1_ONLINE_V2_SHADOW";
        return ApiResponse.success(new ScoringStrategyStatusVO(
                onlineVersion,
                CrossSourceScoreV2Strategy.VERSION,
                v2Online,
                stage
        ));
    }
}
