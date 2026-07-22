package com.airadar.cluster.service;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.cluster.vo.HotClusterSummaryVO;
import com.airadar.cluster.vo.HotItemEvidenceVO;
import com.airadar.common.api.PageResponse;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.airadar.scoring.strategy.CrossSourceScoreV2Strategy;
import com.airadar.scoring.strategy.ScoringStrategyProperties;
import com.airadar.scoring.service.RuleBasedScoringService;
import com.airadar.scoring.vo.HotScoreVO;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HotClusterQueryService {

    private final HotClusterMapper hotClusterMapper;
    private final HotClusterItemMapper hotClusterItemMapper;
    private final HotItemMapper hotItemMapper;
    private final HotScoreMapper hotScoreMapper;
    private final ScoringStrategyProperties scoringProperties;

    public HotClusterQueryService(
            HotClusterMapper hotClusterMapper,
            HotClusterItemMapper hotClusterItemMapper,
            HotItemMapper hotItemMapper,
            HotScoreMapper hotScoreMapper,
            ScoringStrategyProperties scoringProperties
    ) {
        this.hotClusterMapper = hotClusterMapper;
        this.hotClusterItemMapper = hotClusterItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.hotScoreMapper = hotScoreMapper;
        this.scoringProperties = scoringProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<HotClusterSummaryVO> list(
            int page,
            int size,
            HotClusterSort sort,
            SourceType sourceType,
            Instant from,
            Instant to
    ) {
        return list(page, size, sort, sourceType, from, to, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<HotClusterSummaryVO> list(
            int page,
            int size,
            HotClusterSort sort,
            SourceType sourceType,
            Instant from,
            Instant to,
            String scoringVersionOverride
    ) {
        String scoringVersion = resolveScoringVersion(scoringVersionOverride);
        long total = hotClusterMapper.countActive(sourceType, from, to);
        long offset = (long) (page - 1) * size;
        List<HotClusterSummaryVO> items = hotClusterMapper
                .selectActivePage(sourceType, from, to, sort.name(), scoringVersion, size, offset)
                .stream()
                .map(cluster -> toSummary(cluster, scoringVersionOverride))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public HotClusterDetailVO get(long clusterId) {
        return get(clusterId, null);
    }

    @Transactional(readOnly = true)
    public HotClusterDetailVO get(long clusterId, String scoringVersionOverride) {
        HotClusterEntity cluster = hotClusterMapper.selectById(clusterId);
        if (cluster == null) {
            throw new BusinessException(ErrorCode.HOT_CLUSTER_NOT_FOUND);
        }
        List<HotClusterItemEntity> memberships = activeMemberships(clusterId);
        Map<Long, HotItemEntity> itemById = hotItemMapper
                .selectBatchIds(memberships.stream().map(HotClusterItemEntity::getHotItemId).toList())
                .stream()
                .collect(Collectors.toMap(HotItemEntity::getId, Function.identity()));
        List<HotItemEvidenceVO> evidence = memberships.stream()
                .map(membership -> toEvidence(membership, itemById.get(membership.getHotItemId())))
                .toList();
        List<SourceType> sourceTypes = memberships.stream()
                .map(HotClusterItemEntity::getHotItemId)
                .map(itemById::get)
                .filter(item -> item != null && item.getSourceType() != null)
                .map(HotItemEntity::getSourceType)
                .distinct()
                .toList();
        return new HotClusterDetailVO(
                cluster.getId(),
                cluster.getTitle(),
                cluster.getSummary(),
                cluster.getStatus(),
                cluster.getFirstSeenAt(),
                cluster.getLastSeenAt(),
                evidence.size(),
                sourceTypes,
                latestScore(clusterId, scoringVersionOverride),
                evidence
        );
    }

    /**
     * Returns the persisted score for a cluster under the requested scoring
     * version, falling back to V1 when V2 is requested but unavailable so the
     * caller never sees a missing score row after a Phase 18B rollout.
     */
    public HotScoreVO latestScore(long clusterId, String scoringVersionOverride) {
        String requested = resolveScoringVersion(scoringVersionOverride);
        HotScoreEntity score = findLatestScore(clusterId, requested);
        if (score == null
                && CrossSourceScoreV2Strategy.VERSION.equalsIgnoreCase(requested)
                && !scoringProperties.isV2Online()) {
            // Shadow V2 scores may not exist for older clusters; fall back to V1
            // so detail pages and alerts still show a score.
            score = findLatestScore(clusterId, RuleBasedScoringService.SCORING_VERSION);
        }
        if (score == null) {
            return null;
        }
        return new HotScoreVO(
                score.getTotalScore(),
                score.getScoringVersion(),
                score.getCalculatedAt(),
                score.getScoreComponents()
        );
    }

    /**
     * Returns the persisted score for a cluster under the configured online
     * version. Convenience overload for callers that never need to override.
     */
    public HotScoreVO latestScore(long clusterId) {
        return latestScore(clusterId, null);
    }

    /**
     * Returns the configured online scoring version (lower-cased) used by the
     * list endpoint when no explicit override is provided.
     */
    public String configuredOnlineVersion() {
        return scoringProperties.effectiveOnlineVersion();
    }

    private HotScoreEntity findLatestScore(long clusterId, String scoringVersion) {
        return hotScoreMapper.selectOne(
                new LambdaQueryWrapper<HotScoreEntity>()
                        .eq(HotScoreEntity::getHotClusterId, clusterId)
                        .eq(HotScoreEntity::getScoringVersion, scoringVersion)
                        .orderByDesc(HotScoreEntity::getCalculatedAt)
                        .orderByDesc(HotScoreEntity::getId)
                        .last("LIMIT 1")
        );
    }

    private String resolveScoringVersion(String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(java.util.Locale.ROOT);
        }
        return scoringProperties.effectiveOnlineVersion();
    }

    private HotClusterSummaryVO toSummary(HotClusterEntity cluster, String scoringVersionOverride) {
        List<HotClusterItemEntity> memberships = activeMemberships(cluster.getId());
        Map<Long, HotItemEntity> itemById = hotItemMapper
                .selectBatchIds(memberships.stream().map(HotClusterItemEntity::getHotItemId).toList())
                .stream()
                .collect(Collectors.toMap(HotItemEntity::getId, Function.identity()));
        List<SourceType> sourceTypes = memberships.stream()
                .map(HotClusterItemEntity::getHotItemId)
                .map(itemById::get)
                .filter(item -> item != null && item.getSourceType() != null)
                .map(HotItemEntity::getSourceType)
                .distinct()
                .toList();
        return new HotClusterSummaryVO(
                cluster.getId(),
                cluster.getTitle(),
                cluster.getSummary(),
                cluster.getStatus(),
                cluster.getFirstSeenAt(),
                cluster.getLastSeenAt(),
                memberships.size(),
                sourceTypes,
                latestScore(cluster.getId(), scoringVersionOverride)
        );
    }

    private List<HotClusterItemEntity> activeMemberships(long clusterId) {
        return hotClusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, clusterId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
                        .orderByDesc(HotClusterItemEntity::getIsPrimary)
                        .orderByAsc(HotClusterItemEntity::getAssignedAt)
        );
    }

    private HotItemEvidenceVO toEvidence(
            HotClusterItemEntity membership,
            HotItemEntity item
    ) {
        return new HotItemEvidenceVO(
                item.getId(),
                item.getSourceType(),
                item.getExternalId(),
                item.getTitle(),
                item.getSummary(),
                item.getSourceUrl(),
                item.getAuthor(),
                item.getPublishedAt(),
                membership.getMatchMethod(),
                membership.getMatchScore(),
                membership.getMatchReason(),
                membership.getRuleVersion()
        );
    }
}
