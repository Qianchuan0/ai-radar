package com.airadar.evaluation.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.common.api.PageResponse;
import com.airadar.evaluation.dto.CreateEvaluationCaseRequest;
import com.airadar.evaluation.dto.CreateEvaluationDatasetRequest;
import com.airadar.evaluation.service.EvaluationService;
import com.airadar.evaluation.vo.EvaluationCaseVO;
import com.airadar.evaluation.vo.EvaluationDatasetVO;
import com.airadar.evaluation.vo.EvaluationRunGenerationVO;
import com.airadar.evaluation.vo.EvaluationRunSummaryVO;
import com.airadar.evaluation.vo.EvaluationRunVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/datasets")
    public ResponseEntity<ApiResponse<EvaluationDatasetVO>> createDataset(
            @Valid @RequestBody CreateEvaluationDatasetRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(evaluationService.createDataset(request)));
    }

    @GetMapping("/datasets")
    public ApiResponse<List<EvaluationDatasetVO>> listDatasets() {
        return ApiResponse.success(evaluationService.listDatasets());
    }

    @PostMapping("/datasets/{datasetId}/cases")
    public ResponseEntity<ApiResponse<EvaluationCaseVO>> createCase(
            @PathVariable long datasetId,
            @Valid @RequestBody CreateEvaluationCaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(evaluationService.createCase(datasetId, request)));
    }

    @GetMapping("/datasets/{datasetId}/cases")
    public ApiResponse<List<EvaluationCaseVO>> listCases(@PathVariable long datasetId) {
        return ApiResponse.success(evaluationService.listCases(datasetId));
    }

    @PostMapping("/runs")
    public ApiResponse<EvaluationRunGenerationVO> triggerRun(
            @RequestParam @Min(1) Long datasetId
    ) {
        return ApiResponse.success(evaluationService.triggerRun(datasetId));
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<EvaluationRunSummaryVO>> listRuns(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long datasetId
    ) {
        return ApiResponse.success(evaluationService.listRuns(datasetId, page, size));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<EvaluationRunVO> getRun(@PathVariable @Min(1) long runId) {
        return ApiResponse.success(evaluationService.getRun(runId));
    }
}
