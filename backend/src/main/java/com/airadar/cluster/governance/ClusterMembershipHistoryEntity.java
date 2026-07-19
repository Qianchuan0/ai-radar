package com.airadar.cluster.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * Persistent row for a single membership mutation produced by the
 * governance layer.
 *
 * <p>{@code hotClusterId} is the <em>subject</em> cluster — the cluster the
 * caller invoked the governance operation on. For MOVE / SPLIT it is the
 * target cluster the item(s) landed in; for MERGE it is the winner cluster;
 * for REMOVE it is the source cluster the item was taken out of.
 *
 * <p>{@code fromClusterId} / {@code toClusterId} capture the membership
 * transition itself: {@code fromClusterId == null} means the item had no
 * prior membership, {@code toClusterId == null} means the item ended up
 * without an active membership.
 */
@TableName("cluster_membership_history")
public class ClusterMembershipHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hotClusterId;
    private Long hotItemId;
    private String action;
    private Long fromClusterId;
    private Long toClusterId;
    private String reason;
    private String operatorType;
    private String operatorId;
    private Long relatedDecisionId;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHotClusterId() {
        return hotClusterId;
    }

    public void setHotClusterId(Long hotClusterId) {
        this.hotClusterId = hotClusterId;
    }

    public Long getHotItemId() {
        return hotItemId;
    }

    public void setHotItemId(Long hotItemId) {
        this.hotItemId = hotItemId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getFromClusterId() {
        return fromClusterId;
    }

    public void setFromClusterId(Long fromClusterId) {
        this.fromClusterId = fromClusterId;
    }

    public Long getToClusterId() {
        return toClusterId;
    }

    public void setToClusterId(Long toClusterId) {
        this.toClusterId = toClusterId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Long getRelatedDecisionId() {
        return relatedDecisionId;
    }

    public void setRelatedDecisionId(Long relatedDecisionId) {
        this.relatedDecisionId = relatedDecisionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
