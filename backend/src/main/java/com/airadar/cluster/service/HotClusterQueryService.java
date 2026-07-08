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

    public HotClusterQueryService(
            HotClusterMapper hotClusterMapper,
            HotClusterItemMapper hotClusterItemMapper,
            HotItemMapper hotItemMapper,
            HotScoreMapper hotScoreMapper
    ) {
        this.hotClusterMapper = hotClusterMapper;
        this.hotClusterItemMapper = hotClusterItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.hotScoreMapper = hotScoreMapper;
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
        long total = hotClusterMapper.countActive(sourceType, from, to);
        long offset = (long) (page - 1) * size;
        List<HotClusterSummaryVO> items = hotClusterMapper
                .selectActivePage(sourceType, from, to, sort.name(), size, offset)
                .stream()
                .map(this::toSummary)
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public HotClusterDetailVO get(long clusterId) {
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
        return new HotClusterDetailVO(
                cluster.getId(),
                cluster.getTitle(),
                cluster.getSummary(),
                cluster.getStatus(),
                cluster.getFirstSeenAt(),
                cluster.getLastSeenAt(),
                evidence.size(),
                latestScore(clusterId),
                evidence
        );
    }

    private HotClusterSummaryVO toSummary(HotClusterEntity cluster) {
        int itemCount = Math.toIntExact(hotClusterItemMapper.selectCount(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, cluster.getId())
                        .isNull(HotClusterItemEntity::getRemovedAt)
        ));
        return new HotClusterSummaryVO(
                cluster.getId(),
                cluster.getTitle(),
                cluster.getSummary(),
                cluster.getStatus(),
                cluster.getFirstSeenAt(),
                cluster.getLastSeenAt(),
                itemCount,
                latestScore(cluster.getId())
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

    private HotScoreVO latestScore(long clusterId) {
        HotScoreEntity score = hotScoreMapper.selectOne(
                new LambdaQueryWrapper<HotScoreEntity>()
                        .eq(HotScoreEntity::getHotClusterId, clusterId)
                        .orderByDesc(HotScoreEntity::getCalculatedAt)
                        .orderByDesc(HotScoreEntity::getId)
                        .last("LIMIT 1")
        );
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
