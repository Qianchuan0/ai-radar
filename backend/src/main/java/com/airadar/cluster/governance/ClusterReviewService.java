package com.airadar.cluster.governance;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.governance.vo.ClusterReviewTaskVO;
import com.airadar.cluster.governance.vo.MoveItemResultVO;
import com.airadar.cluster.governance.vo.ReviewResolutionVO;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.strategy.ClusterMatchDecisionEntity;
import com.airadar.cluster.strategy.ClusterMatchDecisionMapper;
import com.airadar.common.api.PageResponse;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backend for the {@code /api/v1/cluster-review/tasks} API family.
 *
 * <p>The Phase 16 V2 strategy only ever inserts {@code cluster_match_decision}
 * rows; {@link ReviewTaskStatus#OPEN} review tasks are materialized lazily
 * from any REVIEW_REQUIRED decision that has no matching task yet. This
 * keeps the strategy untouched while still giving the governance API a
 * state-machine table to update.
 *
 * <p>{@link #accept(long, String, String)} resolves a task by also moving the
 * underlying hot item into the candidate cluster via {@link MoveItemService}.
 * {@link #reject(long, String, String)} and {@link #skip(long, String, String)}
 * leave the online membership unchanged.
 */
@Service
public class ClusterReviewService {

    private static final String REVIEW_REQUIRED = "REVIEW_REQUIRED";

    private final ClusterReviewTaskMapper taskMapper;
    private final ClusterMatchDecisionMapper decisionMapper;
    private final HotClusterMapper clusterMapper;
    private final HotClusterItemMapper clusterItemMapper;
    private final MoveItemService moveItemService;

    public ClusterReviewService(
            ClusterReviewTaskMapper taskMapper,
            ClusterMatchDecisionMapper decisionMapper,
            HotClusterMapper clusterMapper,
            HotClusterItemMapper clusterItemMapper,
            MoveItemService moveItemService
    ) {
        this.taskMapper = taskMapper;
        this.decisionMapper = decisionMapper;
        this.clusterMapper = clusterMapper;
        this.clusterItemMapper = clusterItemMapper;
        this.moveItemService = moveItemService;
    }

    @Transactional
    public PageResponse<ClusterReviewTaskVO> list(String statusFilter, int page, int size) {
        if (page < 1 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "Page must be >= 1 and size must be between 1 and 200.");
        }
        materializeOpenTasks();

        LambdaQueryWrapper<ClusterReviewTaskEntity> countWrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<ClusterReviewTaskEntity> listWrapper = new LambdaQueryWrapper<ClusterReviewTaskEntity>()
                .orderByDesc(ClusterReviewTaskEntity::getCreatedAt)
                .orderByDesc(ClusterReviewTaskEntity::getId);
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            try {
                ReviewTaskStatus parsed = ReviewTaskStatus.valueOf(statusFilter.toUpperCase());
                countWrapper.eq(ClusterReviewTaskEntity::getStatus, parsed.name());
                listWrapper.eq(ClusterReviewTaskEntity::getStatus, parsed.name());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "Unknown review status: " + statusFilter);
            }
        }
        long total = taskMapper.selectCount(countWrapper);
        long offset = (long) (page - 1) * size;
        listWrapper.last("LIMIT " + size + " OFFSET " + offset);
        List<ClusterReviewTaskEntity> tasks = taskMapper.selectList(listWrapper);

        if (tasks.isEmpty()) {
            return PageResponse.of(List.of(), page, size, total);
        }
        List<Long> decisionIds = tasks.stream().map(ClusterReviewTaskEntity::getClusterMatchDecisionId).toList();
        Map<Long, ClusterMatchDecisionEntity> decisionById = new HashMap<>();
        for (ClusterMatchDecisionEntity d : decisionMapper.selectBatchIds(decisionIds)) {
            decisionById.put(d.getId(), d);
        }
        List<ClusterReviewTaskVO> rows = new ArrayList<>(tasks.size());
        for (ClusterReviewTaskEntity task : tasks) {
            rows.add(toVO(task, decisionById.get(task.getClusterMatchDecisionId())));
        }
        return PageResponse.of(rows, page, size, total);
    }

    @Transactional
    public ClusterReviewTaskVO get(long taskId) {
        materializeOpenTasks();
        ClusterReviewTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.CLUSTER_REVIEW_TASK_NOT_FOUND);
        }
        ClusterMatchDecisionEntity decision = decisionMapper.selectById(task.getClusterMatchDecisionId());
        return toVO(task, decision);
    }

    @Transactional
    public ReviewResolutionVO accept(long taskId, String reason, String operatorId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Reason is required for accept.");
        }
        materializeOpenTasks();
        ClusterReviewTaskEntity task = loadOpenTask(taskId);
        ClusterMatchDecisionEntity decision = decisionMapper.selectById(task.getClusterMatchDecisionId());
        if (decision == null) {
            throw new BusinessException(ErrorCode.CLUSTER_REVIEW_TASK_NOT_FOUND);
        }
        if (decision.getCandidateClusterId() == null) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Review task has no candidate cluster to accept into.");
        }
        HotClusterEntity candidate = clusterMapper.selectById(decision.getCandidateClusterId());
        if (candidate == null || !"ACTIVE".equals(candidate.getStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Candidate cluster is no longer available for merge.");
        }

        Long historyId = null;
        HotClusterItemEntity membership = findActiveMembership(decision.getHotItemId());
        if (membership != null && !decision.getCandidateClusterId().equals(membership.getHotClusterId())) {
            MoveItemResultVO moveResult = moveItemService.move(
                    membership.getHotClusterId(),
                    decision.getHotItemId(),
                    decision.getCandidateClusterId(),
                    reason,
                    operatorId
            );
            historyId = moveResult.historyId();
        }

        Instant now = Instant.now();
        task.setStatus(ReviewTaskStatus.ACCEPTED.name());
        task.setResolutionReason(reason);
        task.setResolvedAt(now);
        taskMapper.updateById(task);

        return new ReviewResolutionVO(
                task.getId(),
                task.getClusterMatchDecisionId(),
                task.getStatus(),
                reason,
                now,
                historyId
        );
    }

    @Transactional
    public ReviewResolutionVO reject(long taskId, String reason, String operatorId) {
        return resolve(taskId, ReviewTaskStatus.REJECTED, reason);
    }

    @Transactional
    public ReviewResolutionVO skip(long taskId, String reason, String operatorId) {
        return resolve(taskId, ReviewTaskStatus.SKIPPED, reason);
    }

    private ReviewResolutionVO resolve(long taskId, ReviewTaskStatus status, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT,
                    "Reason is required for " + status.name().toLowerCase() + ".");
        }
        materializeOpenTasks();
        ClusterReviewTaskEntity task = loadOpenTask(taskId);
        Instant now = Instant.now();
        task.setStatus(status.name());
        task.setResolutionReason(reason);
        task.setResolvedAt(now);
        taskMapper.updateById(task);
        return new ReviewResolutionVO(
                task.getId(),
                task.getClusterMatchDecisionId(),
                task.getStatus(),
                reason,
                now,
                null
        );
    }

    private ClusterReviewTaskEntity loadOpenTask(long taskId) {
        ClusterReviewTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.CLUSTER_REVIEW_TASK_NOT_FOUND);
        }
        if (!ReviewTaskStatus.OPEN.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET,
                    "Review task is already " + task.getStatus() + ".");
        }
        return task;
    }

    private HotClusterItemEntity findActiveMembership(Long hotItemId) {
        if (hotItemId == null) {
            return null;
        }
        return clusterItemMapper.selectOne(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotItemId, hotItemId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
    }

    /**
     * Inserts an OPEN {@code cluster_review_task} row for every REVIEW_REQUIRED
     * decision that does not yet have one. Idempotent thanks to the unique
     * index on {@code cluster_match_decision_id}.
     *
     * <p>Phase 17C V2 online writes call this proactively after each V2
     * evaluation so REVIEW_REQUIRED decisions surface in the review queue
     * without waiting for the next {@code GET /api/v1/cluster-review/tasks}
     * poll. The method is safe to call repeatedly because of the unique
     * index — concurrent materialization races collapse into a no-op.
     */
    public void materializeOpenTasks() {
        List<ClusterMatchDecisionEntity> reviewRequired = decisionMapper.selectList(
                new LambdaQueryWrapper<ClusterMatchDecisionEntity>()
                        .eq(ClusterMatchDecisionEntity::getDecision, REVIEW_REQUIRED)
                        .orderByDesc(ClusterMatchDecisionEntity::getDecidedAt)
        );
        if (reviewRequired.isEmpty()) {
            return;
        }
        List<Long> decisionIds = reviewRequired.stream().map(ClusterMatchDecisionEntity::getId).toList();
        Set<Long> existing = new HashSet<>();
        for (ClusterReviewTaskEntity existingTask : taskMapper.selectList(
                new LambdaQueryWrapper<ClusterReviewTaskEntity>()
                        .in(ClusterReviewTaskEntity::getClusterMatchDecisionId, decisionIds))) {
            existing.add(existingTask.getClusterMatchDecisionId());
        }
        for (ClusterMatchDecisionEntity decision : reviewRequired) {
            if (existing.contains(decision.getId())) {
                continue;
            }
            ClusterReviewTaskEntity task = new ClusterReviewTaskEntity();
            task.setClusterMatchDecisionId(decision.getId());
            task.setStatus(ReviewTaskStatus.OPEN.name());
            task.setCreatedAt(decision.getDecidedAt() == null ? Instant.now() : decision.getDecidedAt());
            try {
                taskMapper.insert(task);
            } catch (DuplicateKeyException ex) {
                // Concurrent materialization — another transaction inserted
                // the same task row. Safe to skip.
            }
        }
    }

    private ClusterReviewTaskVO toVO(ClusterReviewTaskEntity task, ClusterMatchDecisionEntity decision) {
        return new ClusterReviewTaskVO(
                task.getId(),
                task.getClusterMatchDecisionId(),
                decision == null ? null : decision.getHotItemId(),
                decision == null ? null : decision.getCandidateClusterId(),
                decision == null ? null : decision.getDecision(),
                decision == null ? null : decision.getMatchScore(),
                decision == null ? null : decision.getMatchMethod(),
                decision == null ? null : decision.getMatchReason(),
                decision == null ? null : decision.getRuleVersion(),
                decision == null ? null : decision.getDecidedAt(),
                task.getStatus(),
                task.getResolutionReason(),
                task.getResolvedAt(),
                task.getCreatedAt()
        );
    }
}
