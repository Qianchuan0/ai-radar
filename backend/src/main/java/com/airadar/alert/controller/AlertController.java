package com.airadar.alert.controller;

import com.airadar.alert.dto.UpdateAlertStatusRequest;
import com.airadar.alert.model.AlertStatus;
import com.airadar.alert.service.AlertService;
import com.airadar.alert.vo.AlertMatchingRunVO;
import com.airadar.alert.vo.AlertRecordVO;
import com.airadar.common.api.ApiResponse;
import com.airadar.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/matching-runs")
    public ApiResponse<AlertMatchingRunVO> runMatching() {
        return ApiResponse.success(alertService.runMatching());
    }

    @GetMapping
    public ApiResponse<PageResponse<AlertRecordVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long subscriptionId,
            @RequestParam(required = false) AlertStatus status
    ) {
        return ApiResponse.success(alertService.list(page, size, subscriptionId, status));
    }

    @PatchMapping("/{alertId}/status")
    public ApiResponse<AlertRecordVO> updateStatus(
            @PathVariable long alertId,
            @Valid @RequestBody UpdateAlertStatusRequest request
    ) {
        return ApiResponse.success(alertService.updateStatus(alertId, request.status()));
    }
}
