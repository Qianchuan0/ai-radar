package com.airadar.crawl.controller;

import com.airadar.common.api.ApiResponse;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.crawl.service.CrawlTaskLifecycleService;
import com.airadar.crawl.vo.CrawlTaskVO;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
