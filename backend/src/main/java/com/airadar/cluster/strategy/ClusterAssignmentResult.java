package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable result of a single {@link ClusterAssignmentStrategy#assign} call.
 *
 * <p>The result captures everything Phase 16 needs to persist for
 * traceability: the assigned cluster, the decision outcome, a stable match
 * method tag, a numeric score, a structured match reason, and the rejected or
 * review-required candidate (when applicable).
 */
public final class ClusterAssignmentResult {

    private final HotClusterEntity cluster;
    private final AssignmentDecision decision;
    private final String matchMethod;
    private final BigDecimal matchScore;
    private final JsonNode matchReason;
    private final Long candidateClusterId;
    private final String ruleVersion;

    private ClusterAssignmentResult(Builder builder) {
        this.cluster = Objects.requireNonNull(builder.cluster, "cluster");
        this.decision = Objects.requireNonNull(builder.decision, "decision");
        this.matchMethod = Objects.requireNonNull(builder.matchMethod, "matchMethod");
        this.matchScore = builder.matchScore;
        this.matchReason = builder.matchReason;
        this.candidateClusterId = builder.candidateClusterId;
        this.ruleVersion = Objects.requireNonNull(builder.ruleVersion, "ruleVersion");
    }

    public HotClusterEntity getCluster() {
        return cluster;
    }

    public AssignmentDecision getDecision() {
        return decision;
    }

    public String getMatchMethod() {
        return matchMethod;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public JsonNode getMatchReason() {
        return matchReason;
    }

    public Long getCandidateClusterId() {
        return candidateClusterId;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private HotClusterEntity cluster;
        private AssignmentDecision decision;
        private String matchMethod;
        private BigDecimal matchScore;
        private JsonNode matchReason;
        private Long candidateClusterId;
        private String ruleVersion;

        public Builder cluster(HotClusterEntity cluster) {
            this.cluster = cluster;
            return this;
        }

        public Builder decision(AssignmentDecision decision) {
            this.decision = decision;
            return this;
        }

        public Builder matchMethod(String matchMethod) {
            this.matchMethod = matchMethod;
            return this;
        }

        public Builder matchScore(BigDecimal matchScore) {
            this.matchScore = matchScore;
            return this;
        }

        public Builder matchReason(JsonNode matchReason) {
            this.matchReason = matchReason;
            return this;
        }

        public Builder candidateClusterId(Long candidateClusterId) {
            this.candidateClusterId = candidateClusterId;
            return this;
        }

        public Builder ruleVersion(String ruleVersion) {
            this.ruleVersion = ruleVersion;
            return this;
        }

        public ClusterAssignmentResult build() {
            return new ClusterAssignmentResult(this);
        }
    }
}
