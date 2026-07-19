package com.airadar.cluster.governance;

import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.governance.vo.ReclusterResultVO;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.strategy.ClusterMatchDecisionEntity;
import com.airadar.cluster.strategy.EventRuleClusterStrategy;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Re-runs the V2 strategy for selected items.
 *
 * <p>The V2 run writes only {@code cluster_match_decision} rows (shadow
 * mode). The online {@code hot_cluster_item} membership is not touched —
 * operators must follow up with an explicit merge / split / move once they
 * agree on the right action. A {@code cluster_membership_history} row with
 * action {@link MembershipAction#RECLUSTER} is written per re-evaluated item
 * so the governance audit trail captures the trigger.
 *
 * <p>Rejections:
 * <ul>
 *   <li>Any item id is not an active member of the source cluster →
 *       {@link ErrorCode#CLUSTER_GOVERNANCE_INVALID_ARGUMENT}.</li>
 *   <li>Item id missing in the database →
 *       {@link ErrorCode#HOT_ITEM_NOT_FOUND}.</li>
 * </ul>
 */
@Service
public class ReclusterService {

    private final HotItemMapper hotItemMapper;
    private final HotClusterItemMapper clusterItemMapper;
    private final EventRuleClusterStrategy v2Strategy;
    private final MembershipHistoryRecorder historyRecorder;

    public ReclusterService(
            HotItemMapper hotItemMapper,
            HotClusterItemMapper clusterItemMapper,
            EventRuleClusterStrategy v2Strategy,
            MembershipHistoryRecorder historyRecorder
    ) {
        this.hotItemMapper = hotItemMapper;
        this.clusterItemMapper = clusterItemMapper;
        this.v2Strategy = v2Strategy;
        this.historyRecorder = historyRecorder;
    }

    @Transactional
    public ReclusterResultVO recluster(
            long sourceClusterId,
            List<Long> itemIds,
            String reason,
            String operatorId
    ) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Reason is required for recluster.");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "At least one item id is required for recluster.");
        }

        Set<Long> requestedIds = new HashSet<>(itemIds);
        List<HotClusterItemEntity> activeMemberships = clusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, sourceClusterId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        Set<Long> activeItemIds = new HashSet<>();
        for (HotClusterItemEntity membership : activeMemberships) {
            if (membership.getHotItemId() != null) {
                activeItemIds.add(membership.getHotItemId());
            }
        }
        if (!activeItemIds.containsAll(requestedIds)) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "One or more item ids are not active members of the source cluster.");
        }

        Instant evaluatedAt = Instant.now();
        List<ReclusterResultVO.ReclusteredDecision> decisions = new ArrayList<>();
        List<Long> historyIds = new ArrayList<>();

        for (Long itemId : itemIds) {
            HotItemEntity item = hotItemMapper.selectById(itemId);
            if (item == null) {
                throw new BusinessException(ErrorCode.HOT_ITEM_NOT_FOUND);
            }
            List<ClusterMatchDecisionEntity> produced = v2Strategy.evaluate(item);
            for (ClusterMatchDecisionEntity decision : produced) {
                decisions.add(new ReclusterResultVO.ReclusteredDecision(
                        decision.getHotItemId(),
                        decision.getCandidateClusterId(),
                        decision.getDecision(),
                        decision.getMatchMethod(),
                        decision.getMatchScore() == null ? null : decision.getMatchScore().toPlainString(),
                        decision.getRuleVersion()
                ));
            }
            ClusterMembershipHistoryEntity row = historyRecorder.record(
                    sourceClusterId,
                    itemId,
                    MembershipAction.RECLUSTER,
                    sourceClusterId,
                    null,
                    reason,
                    OperatorType.MANUAL,
                    operatorId,
                    produced.isEmpty() ? null : produced.get(produced.size() - 1).getId()
            );
            historyIds.add(row.getId());
        }

        return new ReclusterResultVO(evaluatedAt, itemIds.size(), decisions, historyIds);
    }
}
