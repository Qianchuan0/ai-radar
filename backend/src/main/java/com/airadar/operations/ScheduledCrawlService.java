package com.airadar.operations;

import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.crawl.vo.CrawlTaskVO;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduledCrawlService {

    static final String SKIP_REASON_IN_FLIGHT = "IN_FLIGHT_TASK";
    static final String SKIP_REASON_NOT_YET_DUE = "NOT_YET_DUE";
    static final String SKIP_REASON_SOURCE_DISABLED = "SOURCE_DISABLED";

    private static final Logger log = LoggerFactory.getLogger(ScheduledCrawlService.class);

    private final SourceConfigMapper sourceConfigMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final CrawlExecutionService crawlExecutionService;

    public ScheduledCrawlService(
            SourceConfigMapper sourceConfigMapper,
            CrawlTaskMapper crawlTaskMapper,
            CrawlExecutionService crawlExecutionService
    ) {
        this.sourceConfigMapper = sourceConfigMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.crawlExecutionService = crawlExecutionService;
    }

    public ScheduledCrawlResult runOnce() {
        Instant startedAt = Instant.now();
        List<SourceConfigEntity> candidates = findScheduledCandidates();
        List<ScheduledSourceResult> results = new ArrayList<>(candidates.size());
        int triggered = 0;
        int skipped = 0;
        int failed = 0;

        for (SourceConfigEntity source : candidates) {
            ScheduledSourceResult result = processSource(source);
            results.add(result);
            if (result.triggered()) {
                triggered++;
            } else if ("ERROR".equals(result.status())) {
                failed++;
            } else {
                skipped++;
            }
        }

        return new ScheduledCrawlResult(
                startedAt,
                Instant.now(),
                candidates.size(),
                triggered,
                skipped,
                failed,
                results
        );
    }

    private List<SourceConfigEntity> findScheduledCandidates() {
        return sourceConfigMapper.selectList(new LambdaQueryWrapper<SourceConfigEntity>()
                .eq(SourceConfigEntity::getEnabled, Boolean.TRUE)
                .isNotNull(SourceConfigEntity::getCrawlIntervalMinutes)
                .orderByAsc(SourceConfigEntity::getId));
    }

    private ScheduledSourceResult processSource(SourceConfigEntity source) {
        long sourceId = source.getId();
        Integer intervalMinutes = source.getCrawlIntervalMinutes();
        if (intervalMinutes == null || intervalMinutes <= 0) {
            return skip(source, null, "INVALID_INTERVAL");
        }
        if (!Boolean.TRUE.equals(source.getEnabled())) {
            return skip(source, null, SKIP_REASON_SOURCE_DISABLED);
        }

        Long inFlight = crawlTaskMapper.selectCount(new LambdaQueryWrapper<CrawlTaskEntity>()
                .eq(CrawlTaskEntity::getSourceConfigId, sourceId)
                .in(CrawlTaskEntity::getStatus, List.of(CrawlTaskStatus.PENDING, CrawlTaskStatus.RUNNING)));
        if (inFlight != null && inFlight > 0) {
            return skip(source, null, SKIP_REASON_IN_FLIGHT);
        }

        Instant now = Instant.now();
        CrawlTaskEntity latest = crawlTaskMapper.selectOne(new LambdaQueryWrapper<CrawlTaskEntity>()
                .eq(CrawlTaskEntity::getSourceConfigId, sourceId)
                .orderByDesc(CrawlTaskEntity::getRequestedAt)
                .last("LIMIT 1"));
        if (latest != null) {
            Instant nextDueAt = latest.getRequestedAt().plus(intervalMinutes, ChronoUnit.MINUTES);
            if (nextDueAt.isAfter(now)) {
                return skip(source, nextDueAt, SKIP_REASON_NOT_YET_DUE);
            }
        }

        String idempotencyKey = buildIdempotencyKey(sourceId, intervalMinutes, now);
        try {
            CrawlTaskVO task = crawlExecutionService.executeScheduled(sourceId, idempotencyKey);
            return new ScheduledSourceResult(
                    sourceId,
                    source.getSourceCode(),
                    true,
                    task.id(),
                    task.status().name(),
                    null,
                    now
            );
        } catch (RuntimeException exception) {
            log.warn("Scheduled crawl for source {} failed: {}", sourceId, exception.getMessage());
            return new ScheduledSourceResult(
                    sourceId,
                    source.getSourceCode(),
                    false,
                    null,
                    "ERROR",
                    exception.getClass().getSimpleName(),
                    now
            );
        }
    }

    private ScheduledSourceResult skip(SourceConfigEntity source, Instant nextDueAt, String reason) {
        return new ScheduledSourceResult(
                source.getId(),
                source.getSourceCode(),
                false,
                null,
                null,
                reason,
                Instant.now()
        );
    }

    static String buildIdempotencyKey(long sourceId, int intervalMinutes, Instant now) {
        long intervalSeconds = Math.max(1L, intervalMinutes) * 60L;
        long bucket = now.getEpochSecond() / intervalSeconds;
        return "scheduled-crawl-" + sourceId + "-" + bucket;
    }
}
