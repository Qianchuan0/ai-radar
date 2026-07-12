package com.airadar.crawl.service;

import com.airadar.common.api.PageResponse;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.entity.CrawlTaskErrorEntity;
import com.airadar.crawl.mapper.CrawlTaskErrorMapper;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlStage;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.crawl.vo.CrawlTaskErrorVO;
import com.airadar.crawl.vo.CrawlTaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CrawlTaskLifecycleService {

    private final CrawlTaskMapper crawlTaskMapper;
    private final CrawlTaskErrorMapper crawlTaskErrorMapper;

    public CrawlTaskLifecycleService(
            CrawlTaskMapper crawlTaskMapper,
            CrawlTaskErrorMapper crawlTaskErrorMapper
    ) {
        this.crawlTaskMapper = crawlTaskMapper;
        this.crawlTaskErrorMapper = crawlTaskErrorMapper;
    }

    public TaskCreationResult createOrGet(long sourceId, String idempotencyKey) {
        return createOrGet(sourceId, idempotencyKey, CrawlTriggerType.MANUAL);
    }

    public TaskCreationResult createOrGet(long sourceId, String idempotencyKey, CrawlTriggerType triggerType) {
        CrawlTaskEntity existing = findByIdempotencyKey(sourceId, idempotencyKey);
        if (existing != null) {
            return new TaskCreationResult(existing, false);
        }

        Instant now = Instant.now();
        CrawlTaskEntity task = new CrawlTaskEntity();
        task.setSourceConfigId(sourceId);
        task.setTriggerType(triggerType);
        task.setStatus(CrawlTaskStatus.PENDING);
        task.setIdempotencyKey(idempotencyKey);
        task.setRequestedAt(now);
        task.setFetchedCount(0);
        task.setPersistedCount(0);
        task.setMatchedCount(0);
        task.setFailedCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            crawlTaskMapper.insert(task);
            return new TaskCreationResult(task, true);
        } catch (DuplicateKeyException exception) {
            return new TaskCreationResult(findByIdempotencyKey(sourceId, idempotencyKey), false);
        }
    }

    @Transactional
    public void markRunning(long taskId) {
        CrawlTaskEntity update = new CrawlTaskEntity();
        update.setId(taskId);
        update.setStatus(CrawlTaskStatus.RUNNING);
        update.setStartedAt(Instant.now());
        update.setUpdatedAt(Instant.now());
        crawlTaskMapper.updateById(update);
    }

    @Transactional
    public void complete(
            long taskId,
            int fetchedCount,
            int persistedCount,
            int matchedCount,
            int failedCount
    ) {
        CrawlTaskStatus status = failedCount == 0 ? CrawlTaskStatus.SUCCEEDED : CrawlTaskStatus.PARTIAL_FAILED;
        CrawlTaskEntity update = new CrawlTaskEntity();
        update.setId(taskId);
        update.setStatus(status);
        update.setFetchedCount(fetchedCount);
        update.setPersistedCount(persistedCount);
        update.setMatchedCount(matchedCount);
        update.setFailedCount(failedCount);
        update.setFinishedAt(Instant.now());
        update.setUpdatedAt(Instant.now());
        crawlTaskMapper.updateById(update);
    }

    @Transactional
    public void fail(long taskId, ErrorCode errorCode, String message) {
        CrawlTaskEntity update = new CrawlTaskEntity();
        update.setId(taskId);
        update.setStatus(CrawlTaskStatus.FAILED);
        update.setFailureCode(errorCode.getCode());
        update.setFailureMessage(truncate(message, 1000));
        update.setFinishedAt(Instant.now());
        update.setUpdatedAt(Instant.now());
        crawlTaskMapper.updateById(update);
    }

    @Transactional
    public void recordError(
            long taskId,
            CrawlStage stage,
            String externalId,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        CrawlTaskErrorEntity entity = new CrawlTaskErrorEntity();
        entity.setCrawlTaskId(taskId);
        entity.setStage(stage);
        entity.setExternalId(externalId);
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(truncate(errorMessage, 1000));
        entity.setRetryable(retryable);
        entity.setOccurredAt(Instant.now());
        crawlTaskErrorMapper.insert(entity);
    }

    @Transactional(readOnly = true)
    public CrawlTaskVO get(long taskId) {
        CrawlTaskEntity task = crawlTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.CRAWL_TASK_NOT_FOUND);
        }
        List<CrawlTaskErrorVO> errors = crawlTaskErrorMapper.selectList(
                new LambdaQueryWrapper<CrawlTaskErrorEntity>()
                        .eq(CrawlTaskErrorEntity::getCrawlTaskId, taskId)
                        .orderByAsc(CrawlTaskErrorEntity::getOccurredAt)
        ).stream().map(this::toErrorVO).toList();
        return toVO(task, errors);
    }

    @Transactional(readOnly = true)
    public PageResponse<CrawlTaskVO> list(
            int page,
            int size,
            Long sourceId,
            CrawlTriggerType triggerType,
            CrawlTaskStatus status
    ) {
        LambdaQueryWrapper<CrawlTaskEntity> filter = new LambdaQueryWrapper<CrawlTaskEntity>()
                .eq(sourceId != null, CrawlTaskEntity::getSourceConfigId, sourceId)
                .eq(triggerType != null, CrawlTaskEntity::getTriggerType, triggerType)
                .eq(status != null, CrawlTaskEntity::getStatus, status);
        long total = crawlTaskMapper.selectCount(filter);
        long offset = (long) (page - 1) * size;
        List<CrawlTaskVO> items = crawlTaskMapper.selectList(filter
                        .orderByDesc(CrawlTaskEntity::getRequestedAt)
                        .orderByDesc(CrawlTaskEntity::getId)
                        .last("LIMIT " + size + " OFFSET " + offset))
                .stream()
                .map(task -> toVO(task, List.of()))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    private CrawlTaskEntity findByIdempotencyKey(long sourceId, String idempotencyKey) {
        return crawlTaskMapper.selectOne(
                new LambdaQueryWrapper<CrawlTaskEntity>()
                        .eq(CrawlTaskEntity::getSourceConfigId, sourceId)
                        .eq(CrawlTaskEntity::getIdempotencyKey, idempotencyKey)
        );
    }

    private CrawlTaskVO toVO(CrawlTaskEntity task, List<CrawlTaskErrorVO> errors) {
        return new CrawlTaskVO(
                task.getId(),
                task.getSourceConfigId(),
                task.getTriggerType(),
                task.getStatus(),
                task.getRetryOfTaskId(),
                task.getRequestedAt(),
                task.getStartedAt(),
                task.getFinishedAt(),
                valueOrZero(task.getFetchedCount()),
                valueOrZero(task.getPersistedCount()),
                valueOrZero(task.getMatchedCount()),
                valueOrZero(task.getFailedCount()),
                task.getFailureCode(),
                task.getFailureMessage(),
                errors
        );
    }

    private CrawlTaskErrorVO toErrorVO(CrawlTaskErrorEntity error) {
        return new CrawlTaskErrorVO(
                error.getStage(),
                error.getExternalId(),
                error.getErrorCode(),
                error.getErrorMessage(),
                Boolean.TRUE.equals(error.getRetryable()),
                error.getOccurredAt()
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record TaskCreationResult(CrawlTaskEntity task, boolean created) {
    }
}
