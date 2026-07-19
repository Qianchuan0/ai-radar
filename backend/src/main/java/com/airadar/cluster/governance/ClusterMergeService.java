package com.airadar.cluster.governance;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.governance.vo.ClusterMergeResultVO;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Merges two ACTIVE clusters into one.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Winner stays {@code ACTIVE}; loser flips to {@code MERGED} and points
 *       its {@code merged_into_cluster_id} at the winner (existing column,
 *       see migration V1).</li>
 *   <li>Every active membership of the loser is moved onto the winner with
 *       {@code assigned_at = now()} and {@code is_primary = false} — primary
 *       re-selection happens once at the end via {@link PrimaryItemSelector}.</li>
 *   <li>One {@code cluster_membership_history} row per moved item is written
 *       with {@link MembershipAction#MERGE}.</li>
 * </ul>
 *
 * <p>Rejections:
 * <ul>
 *   <li>Self-merge (winner == loser) → {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_ARGUMENT}.</li>
 *   <li>Either cluster missing or not {@code ACTIVE} → {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_TARGET}.</li>
 *   <li>Loser already merged (would create a second-level chain) → {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_TARGET}.</li>
 *   <li>Cycle: winner is already merged into loser → {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_TARGET}.</li>
 * </ul>
 */
@Service
public class ClusterMergeService {

    private static final String ACTIVE = "ACTIVE";
    private static final String MERGED = "MERGED";

    private final HotClusterMapper clusterMapper;
    private final HotClusterItemMapper clusterItemMapper;
    private final PrimaryItemSelector primaryItemSelector;
    private final MembershipHistoryRecorder historyRecorder;

    public ClusterMergeService(
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
    public ClusterMergeResultVO merge(long winnerClusterId, long loserClusterId, String reason, String operatorId) {
        if (winnerClusterId == loserClusterId) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Cannot merge a cluster into itself.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Reason is required for cluster merge.");
        }

        HotClusterEntity winner = clusterMapper.selectById(winnerClusterId);
        if (winner == null || !ACTIVE.equals(winner.getStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Winner cluster must exist and be ACTIVE.");
        }
        HotClusterEntity loser = clusterMapper.selectById(loserClusterId);
        if (loser == null || !ACTIVE.equals(loser.getStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Loser cluster must exist and be ACTIVE.");
        }
        if (loser.getMergedIntoClusterId() != null) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Loser cluster has already been merged.");
        }
        if (winner.getMergedIntoClusterId() != null
                && winner.getMergedIntoClusterId().equals(loserClusterId)) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Merge would create a cycle: winner is already merged into loser.");
        }

        List<HotClusterItemEntity> loserMemberships = clusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, loserClusterId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        Instant now = Instant.now();

        if (!loserMemberships.isEmpty()) {
            List<Long> membershipIds = loserMemberships.stream().map(HotClusterItemEntity::getId).toList();
            // Clear primary flags first so the partial unique index on
            // (hot_cluster_id WHERE removed_at IS NULL AND is_primary) cannot
            // transiently hold two rows after the cluster_id update.
            clusterItemMapper.update(null,
                    new LambdaUpdateWrapper<HotClusterItemEntity>()
                            .in(HotClusterItemEntity::getId, membershipIds)
                            .set(HotClusterItemEntity::getIsPrimary, false)
            );
            clusterItemMapper.update(null,
                    new LambdaUpdateWrapper<HotClusterItemEntity>()
                            .in(HotClusterItemEntity::getId, membershipIds)
                            .set(HotClusterItemEntity::getHotClusterId, winnerClusterId)
                            .set(HotClusterItemEntity::getAssignedAt, now)
            );
        }

        loser.setStatus(MERGED);
        loser.setMergedIntoClusterId(winnerClusterId);
        loser.setUpdatedAt(now);
        loser.setVersion((loser.getVersion() == null ? 0 : loser.getVersion()) + 1);
        clusterMapper.updateById(loser);

        // Touch winner so the cluster reflects the merge event in its
        // version / updated_at columns. last_seen_at stays the max of both
        // clusters so existing ranking queries are unaffected.
        if (loser.getLastSeenAt() != null
                && (winner.getLastSeenAt() == null || loser.getLastSeenAt().isAfter(winner.getLastSeenAt()))) {
            winner.setLastSeenAt(loser.getLastSeenAt());
        }
        winner.setUpdatedAt(now);
        winner.setVersion((winner.getVersion() == null ? 0 : winner.getVersion()) + 1);
        clusterMapper.updateById(winner);

        primaryItemSelector.reselect(winner);

        List<Long> historyIds = new ArrayList<>();
        for (HotClusterItemEntity membership : loserMemberships) {
            ClusterMembershipHistoryEntity row = historyRecorder.record(
                    winnerClusterId,
                    membership.getHotItemId(),
                    MembershipAction.MERGE,
                    loserClusterId,
                    winnerClusterId,
                    reason,
                    OperatorType.MANUAL,
                    operatorId,
                    null
            );
            historyIds.add(row.getId());
        }

        return new ClusterMergeResultVO(
                winnerClusterId,
                loserClusterId,
                now,
                loserMemberships.size(),
                historyIds
        );
    }
}
