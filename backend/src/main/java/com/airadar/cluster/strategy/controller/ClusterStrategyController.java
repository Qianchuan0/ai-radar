package com.airadar.cluster.strategy.controller;

import com.airadar.cluster.strategy.ClusterStrategyProperties;
import com.airadar.cluster.strategy.vo.ClusterStrategyStatusVO;
import com.airadar.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 17C internal status endpoint for the cluster strategy stack.
 *
 * <p>Single endpoint {@code GET /api/v1/cluster-strategy/status} returns the
 * online strategy, shadow strategy, V2 online configuration, and the
 * effective rollout stage derived from the configuration. Intended for
 * ops / debugging use during the gradual V2 online rollout.
 */
@RestController
@RequestMapping("/api/v1/cluster-strategy")
public class ClusterStrategyController {

    private final ClusterStrategyProperties properties;

    public ClusterStrategyController(ClusterStrategyProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    public ApiResponse<ClusterStrategyStatusVO> status() {
        ClusterStrategyProperties.V2Online v2Online = properties.getV2Online();
        return ApiResponse.success(new ClusterStrategyStatusVO(
                properties.getStrategy(),
                properties.getShadowStrategy(),
                properties.isShadowEnabled(),
                properties.isV2OnlineEnabled(),
                v2Online.getTrafficPercent(),
                List.copyOf(v2Online.allowedLevelSet()),
                v2Online.getL3MinScore(),
                v2Online.isReviewRequiredToQueue(),
                List.copyOf(v2Online.sourceAllowlistSet()),
                describeRolloutStage(properties, v2Online)
        ));
    }

    /**
     * Maps the configuration onto the rollout stage language from the
     * Phase 17C plan so the status payload is self-describing.
     *
     * <ul>
     *   <li>{@code SHADOW_ONLY}: shadow enabled, V2 online disabled</li>
     *   <li>{@code V1_ONLY}: nothing configured beyond V1</li>
     *   <li>{@code STAGE_2_L1}: V2 online + only L1 allowed</li>
     *   <li>{@code STAGE_3_L2}: V2 online + L1 + L2 allowed</li>
     *   <li>{@code STAGE_4_L3}: V2 online + L3 (with L1/L2 optionally) allowed</li>
     *   <li>{@code STAGE_5_REVIEW}: V2 online with review-required-to-queue</li>
     * </ul>
     */
    private String describeRolloutStage(ClusterStrategyProperties props,
                                        ClusterStrategyProperties.V2Online v2Online) {
        if (!props.isV2OnlineEnabled()) {
            return props.isShadowEnabled() ? "SHADOW_ONLY" : "V1_ONLY";
        }
        java.util.Set<String> levels = v2Online.allowedLevelSet();
        if (levels.contains("L3")) {
            return "STAGE_4_L3";
        }
        if (levels.contains("L2")) {
            return "STAGE_3_L2";
        }
        if (levels.contains("L1")) {
            return "STAGE_2_L1";
        }
        // Misconfiguration — validate() should prevent this, but never hide it.
        return "V2_ONLINE_MISCONFIGURED";
    }
}
