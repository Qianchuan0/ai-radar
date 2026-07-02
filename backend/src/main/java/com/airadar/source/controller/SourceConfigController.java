package com.airadar.source.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.dto.UpdateSourceStatusRequest;
import com.airadar.source.model.SourceType;
import com.airadar.source.service.SourceConfigService;
import com.airadar.source.vo.SourceConfigVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceConfigController {

    private final SourceConfigService sourceConfigService;

    public SourceConfigController(SourceConfigService sourceConfigService) {
        this.sourceConfigService = sourceConfigService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SourceConfigVO>> create(@Valid @RequestBody CreateSourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sourceConfigService.create(request)));
    }

    @GetMapping
    public ApiResponse<List<SourceConfigVO>> list(
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) Boolean enabled
    ) {
        return ApiResponse.success(sourceConfigService.list(sourceType, enabled));
    }

    @PatchMapping("/{sourceId}/status")
    public ApiResponse<SourceConfigVO> updateStatus(
            @PathVariable long sourceId,
            @Valid @RequestBody UpdateSourceStatusRequest request
    ) {
        return ApiResponse.success(sourceConfigService.updateStatus(sourceId, request.enabled()));
    }
}
