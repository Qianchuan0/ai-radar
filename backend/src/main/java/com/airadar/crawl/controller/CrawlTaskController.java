package com.airadar.crawl.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.crawl.service.CrawlTaskLifecycleService;
import com.airadar.crawl.vo.CrawlTaskVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CrawlTaskController {

    private final CrawlExecutionService crawlExecutionService;
    private final CrawlTaskLifecycleService taskLifecycleService;

    public CrawlTaskController(
            CrawlExecutionService crawlExecutionService,
            CrawlTaskLifecycleService taskLifecycleService
    ) {
        this.crawlExecutionService = crawlExecutionService;
        this.taskLifecycleService = taskLifecycleService;
    }

    @PostMapping("/sources/{sourceId}/crawl-tasks")
    public ApiResponse<CrawlTaskVO> startManualCrawl(
            @PathVariable long sourceId,
            @RequestHeader("Idempotency-Key")
            @Size(min = 1, max = 128)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$")
            String idempotencyKey
    ) {
        return ApiResponse.success(crawlExecutionService.executeManual(sourceId, idempotencyKey));
    }

    @GetMapping("/crawl-tasks/{taskId}")
    public ApiResponse<CrawlTaskVO> getTask(@PathVariable long taskId) {
        return ApiResponse.success(taskLifecycleService.get(taskId));
    }

    @GetMapping("/crawl-tasks")
    public ApiResponse<PageResponse<CrawlTaskVO>> listTasks(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) CrawlTriggerType triggerType,
            @RequestParam(required = false) CrawlTaskStatus status
    ) {
        return ApiResponse.success(taskLifecycleService.list(page, size, sourceId, triggerType, status));
    }
}
