package com.airadar.cluster.governance;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.governance.vo.MoveItemResultVO;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.strategy.PrimaryItemSelector;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Moves a single hot item from its current active cluster into a different
 * ACTIVE cluster.
 *
 * <p>The source and destination clusters each have their primary re-selected
 * after the move. If the source cluster becomes empty, it is closed as
 * {@code MERGED} and points at the destination cluster so governance never
 * leaves an ACTIVE cluster without active membership. A
 * {@code cluster_membership_history} row with action {@link MembershipAction#MOVE}
 * is written against the destination cluster.
 *
 * <p>Rejections:
 * <ul>
 *   <li>Item has no active membership at all →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_NO_MEMBERSHIP}.</li>
 *   <li>Item's current active membership is not in the path-source cluster →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_ARGUMENT}.</li>
 *   <li>Target cluster missing, not ACTIVE, or equal to the current source →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_TARGET} /
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_ARGUMENT}.</li>
 * </ul>
 */
@Service
public class MoveItemService {

    private static final String ACTIVE = "ACTIVE";
    private static final String MERGED = "MERGED";

    private final HotClusterMapper clusterMapper;
    private final HotClusterItemMapper clusterItemMapper;
    private final PrimaryItemSelector primaryItemSelector;
    private final MembershipHistoryRecorder historyRecorder;

    public MoveItemService(
            HotClusterMapper clusterMapper,
            HotClusterItemMapper clusterItemMapper,
            PrimaryItemSelector primaryItemSelector,
            MembershipHistoryRecorder historyRecorder
    ) {
        this.clusterMapper = clusterMapper;
        this.clusterItemMapper = clusterItemMapper;
        this.primaryItemSelector = primaryItemSelector;
        this.historyRecorder = historyRecorder;
    }

    @Transactional
    public MoveItemResultVO move(
            long sourceClusterId,
            long hotItemId,
            long targetClusterId,
            String reason,
            String operatorId
    ) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Reason is required for move item.");
        }
        if (sourceClusterId == targetClusterId) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Target cluster must differ from current cluster.");
        }

        HotClusterItemEntity membership = clusterItemMapper.selectOne(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotItemId, hotItemId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        if (membership == null) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_NO_MEMBERSHIP);
        }
        if (membership.getHotClusterId() == null
                || membership.getHotClusterId() != sourceClusterId) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Item is not an active member of the source cluster.");
        }
        long fromClusterId = membership.getHotClusterId();

        HotClusterEntity source = clusterMapper.selectById(fromClusterId);
        if (source == null || !ACTIVE.equals(source.getStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Source cluster must exist and be ACTIVE.");
        }
        HotClusterEntity target = clusterMapper.selectById(targetClusterId);
        if (target == null || !ACTIVE.equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Target cluster must exist and be ACTIVE.");
        }

        Instant now = Instant.now();
        // Clear primary flag on the membership first so we never violate the
        // partial unique index when the target already has its own primary.
        if (Boolean.TRUE.equals(membership.getIsPrimary())) {
            clusterItemMapper.update(null,
                    new LambdaUpdateWrapper<HotClusterItemEntity>()
                            .eq(HotClusterItemEntity::getId, membership.getId())
                            .set(HotClusterItemEntity::getIsPrimary, false)
            );
        }
        clusterItemMapper.update(null,
                new LambdaUpdateWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getId, membership.getId())
                        .set(HotClusterItemEntity::getHotClusterId, targetClusterId)
                        .set(HotClusterItemEntity::getAssignedAt, now)
        );

        target.setUpdatedAt(now);
        target.setVersion((target.getVersion() == null ? 0 : target.getVersion()) + 1);
        clusterMapper.updateById(target);

        if (hasActiveMembership(sourceClusterId)) {
            source.setUpdatedAt(now);
            source.setVersion((source.getVersion() == null ? 0 : source.getVersion()) + 1);
            clusterMapper.updateById(source);
            primaryItemSelector.reselect(source);
        } else {
            source.setStatus(MERGED);
            source.setMergedIntoClusterId(targetClusterId);
            source.setPrimaryItemId(null);
            source.setUpdatedAt(now);
            source.setVersion((source.getVersion() == null ? 0 : source.getVersion()) + 1);
            clusterMapper.updateById(source);
        }
        primaryItemSelector.reselect(target);

        ClusterMembershipHistoryEntity row = historyRecorder.record(
                targetClusterId,
                hotItemId,
                MembershipAction.MOVE,
                fromClusterId,
                targetClusterId,
                reason,
                OperatorType.MANUAL,
                operatorId,
                null
        );

        return new MoveItemResultVO(hotItemId, fromClusterId, targetClusterId, now, row.getId());
    }

    private boolean hasActiveMembership(long clusterId) {
        Long count = clusterItemMapper.selectCount(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, clusterId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        return count != null && count > 0;
    }
}
