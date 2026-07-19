package com.airadar.cluster.strategy;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted row for a single V2 assignment decision.
 *
 * <p>The Phase 16 V2 strategy writes one row per (hot_item, candidate_cluster)
 * pair it considered, including the rejected and review-required candidates
 * V1 would have discarded. This keeps the decision auditable and gives the
 * future Phase 17 governance backend the inputs it needs without rescanning.
 *
 * <p>Allowed {@link #decision} values are constrained by migration V8 to
 * {@code ACCEPTED}, {@code REJECTED}, {@code REVIEW_REQUIRED}, and
 * {@code NO_CANDIDATE}.
 */
@TableName(value = "cluster_match_decision", autoResultMap = true)
public class ClusterMatchDecisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hotItemId;
    private Long candidateClusterId;
    private String decision;
    private BigDecimal matchScore;
    private String matchMethod;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode matchReason;
    private String ruleVersion;
    private Instant decidedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHotItemId() {
        return hotItemId;
    }

    public void setHotItemId(Long hotItemId) {
        this.hotItemId = hotItemId;
    }

    public Long getCandidateClusterId() {
        return candidateClusterId;
    }

    public void setCandidateClusterId(Long candidateClusterId) {
        this.candidateClusterId = candidateClusterId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(BigDecimal matchScore) {
        this.matchScore = matchScore;
    }

    public String getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(String matchMethod) {
        this.matchMethod = matchMethod;
    }

    public JsonNode getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(JsonNode matchReason) {
        this.matchReason = matchReason;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
