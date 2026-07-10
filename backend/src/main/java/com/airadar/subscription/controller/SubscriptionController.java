package com.airadar.subscription.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.subscription.dto.CreateSubscriptionRequest;
import com.airadar.subscription.dto.UpdateSubscriptionStatusRequest;
import com.airadar.subscription.service.SubscriptionService;
import com.airadar.subscription.vo.SubscriptionRuleVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionRuleVO>> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(subscriptionService.create(request)));
    }

    @GetMapping
    public ApiResponse<List<SubscriptionRuleVO>> list() {
        return ApiResponse.success(subscriptionService.list());
    }

    @PatchMapping("/{subscriptionId}/status")
    public ApiResponse<SubscriptionRuleVO> updateStatus(
            @PathVariable long subscriptionId,
            @Valid @RequestBody UpdateSubscriptionStatusRequest request
    ) {
        return ApiResponse.success(subscriptionService.updateStatus(subscriptionId, request.enabled()));
    }
}
