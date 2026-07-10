package com.airadar.report.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.common.api.PageResponse;
import com.airadar.report.service.DailyReportService;
import com.airadar.report.vo.DailyReportGenerationVO;
import com.airadar.report.vo.DailyReportSummaryVO;
import com.airadar.report.vo.DailyReportVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/reports")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    public DailyReportController(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @PostMapping("/daily-runs")
    public ApiResponse<DailyReportGenerationVO> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(dailyReportService.generate(date));
    }

    @GetMapping("/daily")
    public ApiResponse<PageResponse<DailyReportSummaryVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(dailyReportService.list(page, size));
    }

    @GetMapping("/daily/{reportDate}")
    public ApiResponse<DailyReportVO> get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return ApiResponse.success(dailyReportService.get(reportDate));
    }
}
