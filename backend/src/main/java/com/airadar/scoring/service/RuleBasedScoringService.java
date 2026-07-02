package com.airadar.scoring.service;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.airadar.scoring.model.ScoreBreakdown;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class RuleBasedScoringService {

    public static final String SCORING_VERSION = "hn-score-v1";

    private final HotClusterItemMapper hotClusterItemMapper;
    private final HotItemMapper hotItemMapper;
    private final HotScoreMapper hotScoreMapper;
    private final ObjectMapper objectMapper;

    public RuleBasedScoringService(
            HotClusterItemMapper hotClusterItemMapper,
            HotItemMapper hotItemMapper,
            HotScoreMapper hotScoreMapper,
            ObjectMapper objectMapper
    ) {
        this.hotClusterItemMapper = hotClusterItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.hotScoreMapper = hotScoreMapper;
        this.objectMapper = objectMapper;
    }

    public HotScoreEntity score(HotClusterEntity cluster) {
        List<HotClusterItemEntity> memberships = hotClusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, cluster.getId())
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        List<HotItemEntity> items = hotItemMapper.selectBatchIds(
                memberships.stream().map(HotClusterItemEntity::getHotItemId).toList()
        );

        int maxPoints = items.stream().mapToInt(this::points).max().orElse(0);
        int maxComments = items.stream().mapToInt(this::comments).max().orElse(0);
        int maxKeywordCount = items.stream().mapToInt(this::keywordCount).max().orElse(0);
        Instant newestPublishedAt = items.stream()
                .map(HotItemEntity::getPublishedAt)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(Instant.now());

        ScoreBreakdown breakdown = new ScoreBreakdown(
                cappedLogScore(maxPoints, 500, 35),
                cappedLogScore(maxComments, 200, 20),
                freshnessScore(newestPublishedAt),
                Math.min(10, maxKeywordCount * 2.0),
                Math.min(5, Math.max(0, items.size() - 1) * 2.5)
        );
        BigDecimal totalScore = BigDecimal.valueOf(breakdown.total()).setScale(4, RoundingMode.HALF_UP);
        Instant now = Instant.now();

        HotScoreEntity entity = new HotScoreEntity();
        entity.setHotClusterId(cluster.getId());
        entity.setTotalScore(totalScore);
        entity.setScoreComponents(objectMapper.valueToTree(breakdown));
        entity.setScoringVersion(SCORING_VERSION);
        entity.setCalculatedAt(now);
        entity.setCreatedAt(now);
        hotScoreMapper.insert(entity);
        return entity;
    }

    private double cappedLogScore(int value, int cap, int maxScore) {
        int capped = Math.max(0, Math.min(value, cap));
        return Math.log1p(capped) / Math.log1p(cap) * maxScore;
    }

    private double freshnessScore(Instant publishedAt) {
        long ageHours = Math.max(0, Duration.between(publishedAt, Instant.now()).toHours());
        return Math.max(0, 30.0 * (1.0 - ageHours / 72.0));
    }

    private int points(HotItemEntity item) {
        return item.getMetrics() == null ? 0 : item.getMetrics().path("points").asInt(0);
    }

    private int comments(HotItemEntity item) {
        return item.getMetrics() == null ? 0 : item.getMetrics().path("commentsCount").asInt(0);
    }

    private int keywordCount(HotItemEntity item) {
        return item.getTags() != null && item.getTags().isArray() ? item.getTags().size() : 0;
    }
}
