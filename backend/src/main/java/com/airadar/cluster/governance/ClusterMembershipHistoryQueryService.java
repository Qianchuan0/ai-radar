package com.airadar.cluster.governance;

import com.airadar.cluster.governance.vo.MembershipHistoryVO;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only accessor for the {@code cluster_membership_history} table.
 *
 * <p>Governance services write history rows; this service is the single
 * read-side accessor used by the API layer. Keeping the read path separate
 * prevents the mutation services from accidentally exposing mapper queries.
 */
@Service
public class ClusterMembershipHistoryQueryService {

    private final ClusterMembershipHistoryMapper mapper;

    public ClusterMembershipHistoryQueryService(ClusterMembershipHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MembershipHistoryVO> listForCluster(long clusterId, int limit) {
        if (limit <= 0 || limit > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "Limit must be between 1 and 500.");
        }
        LambdaQueryWrapper<ClusterMembershipHistoryEntity> wrapper = new LambdaQueryWrapper<ClusterMembershipHistoryEntity>()
                .eq(ClusterMembershipHistoryEntity::getHotClusterId, clusterId)
                .orderByDesc(ClusterMembershipHistoryEntity::getCreatedAt)
                .orderByDesc(ClusterMembershipHistoryEntity::getId)
                .last("LIMIT " + limit);
        return mapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    private MembershipHistoryVO toVO(ClusterMembershipHistoryEntity row) {
        return new MembershipHistoryVO(
                row.getId(),
                row.getHotClusterId(),
                row.getHotItemId(),
                row.getAction(),
                row.getFromClusterId(),
                row.getToClusterId(),
                row.getReason(),
                row.getOperatorType(),
                row.getOperatorId(),
                row.getRelatedDecisionId(),
                row.getCreatedAt()
        );
    }
}
