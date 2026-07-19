package com.airadar.cluster.governance;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.governance.vo.ClusterSplitResultVO;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.strategy.PrimaryItemSelector;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves a subset of items out of one cluster into another.
 *
 * <p>When no {@code targetClusterId} is provided, a new ACTIVE cluster is
 * created with title / summary / first_seen_at copied from the highest-id
 * moved item. When a target id is provided it must point to an existing
 * ACTIVE cluster distinct from the source.
 *
 * <p>Rejections:
 * <ul>
 *   <li>Any item id with no active membership in the source cluster →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_ARGUMENT}.</li>
 *   <li>Moving every active member out of the source (would leave it empty) →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_ARGUMENT}.</li>
 *   <li>Target cluster missing or not {@code ACTIVE} →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_TARGET}.</li>
 *   <li>Target equals source →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_ARGUMENT}.</li>
 * </ul>
 */
@Service
public class ClusterSplitService {

    private static final String ACTIVE = "ACTIVE";

    private final HotClusterMapper clusterMapper;
    private final HotClusterItemMapper clusterItemMapper;
    private final HotItemMapper hotItemMapper;
    private final PrimaryItemSelector primaryItemSelector;
    private final MembershipHistoryRecorder historyRecorder;

    public ClusterSplitService(
            HotClusterMapper clusterMapper,
            HotClusterItemMapper clusterItemMapper,
            HotItemMapper hotItemMapper,
            PrimaryItemSelector primaryItemSelector,
            MembershipHistoryRecorder historyRecorder
    ) {
        this.clusterMapper = clusterMapper;
        this.clusterItemMapper = clusterItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.primaryItemSelector = primaryItemSelector;
        this.historyRecorder = historyRecorder;
    }

    @Transactional
    public ClusterSplitResultVO split(
            long sourceClusterId,
            List<Long> itemIds,
            Long targetClusterId,
            String reason,
            String operatorId
    ) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Reason is required for cluster split.");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "At least one item id is required for split.");
        }
        if (targetClusterId != null && targetClusterId == sourceClusterId) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Target cluster must differ from source cluster.");
        }

        HotClusterEntity source = clusterMapper.selectById(sourceClusterId);
        if (source == null || !ACTIVE.equals(source.getStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Source cluster must exist and be ACTIVE.");
        }

        Set<Long> requestedIds = new HashSet<>(itemIds);
        List<HotClusterItemEntity> sourceMemberships = clusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, sourceClusterId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        Set<Long> activeItemIds = new HashSet<>();
        for (HotClusterItemEntity membership : sourceMemberships) {
            if (membership.getHotItemId() != null) {
                activeItemIds.add(membership.getHotItemId());
            }
        }
        if (!activeItemIds.containsAll(requestedIds)) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "One or more item ids are not active members of the source cluster.");
        }
        // Prevent leaving the source cluster empty.
        if (requestedIds.containsAll(activeItemIds)) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Cannot split every active member out of a cluster; merge the source into the target instead.");
        }

        HotClusterEntity target = resolveTarget(source, targetClusterId, requestedIds);
        boolean targetCreated = targetClusterId == null;
        Instant now = Instant.now();

        List<HotClusterItemEntity> movedMemberships = sourceMemberships.stream()
                .filter(m -> requestedIds.contains(m.getHotItemId()))
                .toList();
        List<Long> membershipIds = movedMemberships.stream().map(HotClusterItemEntity::getId).toList();
        List<Long> movedItemIds = movedMemberships.stream()
                .map(HotClusterItemEntity::getHotItemId)
                .sorted()
                .toList();

        // Clear primary flags on moved memberships before relocating them so
        // the partial unique index on (hot_cluster_id WHERE is_primary) does
        // not transiently fire when the target already has its own primary.
        clusterItemMapper.update(null,
                new LambdaUpdateWrapper<HotClusterItemEntity>()
                        .in(HotClusterItemEntity::getId, membershipIds)
                        .set(HotClusterItemEntity::getIsPrimary, false)
        );
        clusterItemMapper.update(null,
                new LambdaUpdateWrapper<HotClusterItemEntity>()
                        .in(HotClusterItemEntity::getId, membershipIds)
                        .set(HotClusterItemEntity::getHotClusterId, target.getId())
                        .set(HotClusterItemEntity::getAssignedAt, now)
        );

        source.setUpdatedAt(now);
        source.setVersion((source.getVersion() == null ? 0 : source.getVersion()) + 1);
        clusterMapper.updateById(source);
        target.setUpdatedAt(now);
        target.setVersion((target.getVersion() == null ? 0 : target.getVersion()) + 1);
        clusterMapper.updateById(target);

        primaryItemSelector.reselect(source);
        primaryItemSelector.reselect(target);

        List<Long> historyIds = new ArrayList<>();
        for (Long itemId : movedItemIds) {
            ClusterMembershipHistoryEntity row = historyRecorder.record(
                    target.getId(),
                    itemId,
                    MembershipAction.SPLIT,
                    sourceClusterId,
                    target.getId(),
                    reason,
                    OperatorType.MANUAL,
                    operatorId,
                    null
            );
            historyIds.add(row.getId());
        }

        return new ClusterSplitResultVO(
                sourceClusterId,
                target.getId(),
                targetCreated,
                now,
                movedItemIds,
                historyIds
        );
    }

    private HotClusterEntity resolveTarget(
            HotClusterEntity source,
            Long targetClusterId,
            Set<Long> requestedIds
    ) {
        if (targetClusterId != null) {
            HotClusterEntity target = clusterMapper.selectById(targetClusterId);
            if (target == null || !ACTIVE.equals(target.getStatus())) {
                throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                        "Target cluster must exist and be ACTIVE.");
            }
            return target;
        }
        return createNewTarget(source, requestedIds);
    }

    private HotClusterEntity createNewTarget(HotClusterEntity source, Set<Long> requestedIds) {
        // Use the highest-id moved item to seed the new cluster's metadata so
        // downstream queries have something deterministic to render.
        List<HotItemEntity> movedItems = hotItemMapper.selectBatchIds(requestedIds);
        HotItemEntity seed = movedItems.stream()
                .max((a, b) -> Long.compare(
                        a.getId() == null ? Long.MIN_VALUE : a.getId(),
                        b.getId() == null ? Long.MIN_VALUE : b.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.HOT_ITEM_NOT_FOUND));

        Instant now = Instant.now();
        HotClusterEntity target = new HotClusterEntity();
        target.setTitle(seed.getTitle());
        target.setSummary(seed.getSummary());
        target.setStatus(ACTIVE);
        target.setPrimaryItemId(seed.getId());
        target.setFirstSeenAt(seed.getFirstSeenAt() == null ? now : seed.getFirstSeenAt());
        target.setLastSeenAt(seed.getLastSeenAt() == null ? now : seed.getLastSeenAt());
        target.setVersion(0);
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        clusterMapper.insert(target);
        return target;
    }
}
