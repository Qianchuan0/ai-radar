package com.airadar.health.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.health.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthVO> health() {
        return ApiResponse.success(new HealthVO("UP", "ai-radar-backend"));
    }
}
