package com.airadar.crawl.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.CollectionError;
import com.airadar.crawl.collector.CollectorRegistry;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.model.CrawlStage;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.crawl.service.CrawlTaskLifecycleService.TaskCreationResult;
import com.airadar.crawl.vo.CrawlTaskVO;
import com.airadar.item.service.ItemPipelineService;
import com.airadar.item.service.ItemProcessingException;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.service.RawItemService;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.service.SourceConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CrawlExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CrawlExecutionService.class);

    private final SourceConfigService sourceConfigService;
    private final CollectorRegistry collectorRegistry;
    private final CrawlTaskLifecycleService taskLifecycleService;
    private final RawItemService rawItemService;
    private final ItemPipelineService itemPipelineService;

    public CrawlExecutionService(
            SourceConfigService sourceConfigService,
            CollectorRegistry collectorRegistry,
            CrawlTaskLifecycleService taskLifecycleService,
            RawItemService rawItemService,
            ItemPipelineService itemPipelineService
    ) {
        this.sourceConfigService = sourceConfigService;
        this.collectorRegistry = collectorRegistry;
        this.taskLifecycleService = taskLifecycleService;
        this.rawItemService = rawItemService;
        this.itemPipelineService = itemPipelineService;
    }

    public CrawlTaskVO executeManual(long sourceId, String idempotencyKey) {
        return execute(sourceId, idempotencyKey, CrawlTriggerType.MANUAL);
    }

    public CrawlTaskVO executeScheduled(long sourceId, String idempotencyKey) {
        return execute(sourceId, idempotencyKey, CrawlTriggerType.SCHEDULED);
    }

    private CrawlTaskVO execute(long sourceId, String idempotencyKey, CrawlTriggerType triggerType) {
        SourceConfigEntity sourceConfig = sourceConfigService.findRequired(sourceId);
        if (!Boolean.TRUE.equals(sourceConfig.getEnabled())) {
            throw new BusinessException(ErrorCode.SOURCE_DISABLED);
        }

        TaskCreationResult creation = taskLifecycleService.createOrGet(sourceId, idempotencyKey, triggerType);
        CrawlTaskEntity task = creation.task();
        if (!creation.created()) {
            return taskLifecycleService.get(task.getId());
        }

        taskLifecycleService.markRunning(task.getId());
        try {
            CollectionBatch batch = collectorRegistry.getRequired(sourceConfig.getSourceType()).collect(sourceConfig);
            return processBatch(task.getId(), sourceConfig, batch);
        } catch (BusinessException exception) {
            taskLifecycleService.recordError(
                    task.getId(),
                    CrawlStage.FETCH,
                    null,
                    exception.getErrorCode().getCode(),
                    exception.getMessage(),
                    true
            );
            taskLifecycleService.fail(task.getId(), exception.getErrorCode(), exception.getMessage());
            return taskLifecycleService.get(task.getId());
        } catch (RuntimeException exception) {
            log.error("Unexpected crawl failure for task {}.", task.getId(), exception);
            taskLifecycleService.recordError(
                    task.getId(),
                    CrawlStage.FETCH,
                    null,
                    ErrorCode.INTERNAL_ERROR.getCode(),
                    safeMessage(exception),
                    false
            );
            taskLifecycleService.fail(task.getId(), ErrorCode.INTERNAL_ERROR, safeMessage(exception));
            return taskLifecycleService.get(task.getId());
        }
    }

    private CrawlTaskVO processBatch(long taskId, SourceConfigEntity sourceConfig, CollectionBatch batch) {
        int persistedCount = 0;
        int matchedCount = 0;
        int failedCount = batch.errors().size();

        for (CollectionError error : batch.errors()) {
            taskLifecycleService.recordError(
                    taskId,
                    CrawlStage.FETCH,
                    error.externalId(),
                    error.errorCode(),
                    error.errorMessage(),
                    error.retryable()
            );
        }

        for (CollectedItem collectedItem : batch.items()) {
            RawItemEntity rawItem;
            try {
                rawItem = rawItemService.save(taskId, sourceConfig.getSourceType(), collectedItem);
                persistedCount++;
            } catch (RuntimeException exception) {
                failedCount++;
                taskLifecycleService.recordError(
                        taskId,
                        CrawlStage.PERSIST,
                        collectedItem.externalId(),
                        "CRAWL.RAW_ITEM_PERSIST_FAILED",
                        safeMessage(exception),
                        false
                );
                continue;
            }

            try {
                if (itemPipelineService.process(rawItem, sourceConfig)) {
                    matchedCount++;
                }
            } catch (ItemProcessingException exception) {
                failedCount++;
                taskLifecycleService.recordError(
                        taskId,
                        exception.getStage(),
                        collectedItem.externalId(),
                        "CRAWL.ITEM_PROCESSING_FAILED",
                        safeMessage(exception),
                        false
                );
            }
        }

        taskLifecycleService.complete(
                taskId,
                batch.items().size(),
                persistedCount,
                matchedCount,
                failedCount
        );
        return taskLifecycleService.get(taskId);
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
