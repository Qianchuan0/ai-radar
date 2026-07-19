package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.service.RuleBasedClusterService;
import com.airadar.item.entity.HotItemEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * V1 clustering strategy that transparently wraps the existing
 * {@link RuleBasedClusterService}.
 *
 * <p>This wrapper exists so the {@link ClusterAssignmentOrchestrator} (and the
 * Phase 16A evaluation harness) can treat V1 and V2 uniformly through the
 * {@link ClusterAssignmentStrategy} interface. The delegate's behavior is
 * unchanged: same rule version, same canonical-URL matching, same persistence.
 *
 * <p><b>Zero-behavior-change guarantee:</b> this class adds no clustering logic
 * beyond delegation. The only thing it adds is a thin read-after-write query
 * that surfaces the membership the delegate just created, so callers can see
 * whether the item landed in a singleton cluster
 * ({@link AssignmentDecision#NO_CANDIDATE}) or was merged into an existing one
 * ({@link AssignmentDecision#ACCEPTED}).
 */
@Component
public class CanonicalUrlClusterStrategy implements ClusterAssignmentStrategy {

    public static final String MATCH_METHOD_SINGLETON = "SINGLETON";
    public static final String MATCH_METHOD_CANONICAL_URL = "CANONICAL_URL";

    private final RuleBasedClusterService delegate;
    private final HotClusterItemMapper clusterItemMapper;
    private final ObjectMapper objectMapper;

    public CanonicalUrlClusterStrategy(
            RuleBasedClusterService delegate,
            HotClusterItemMapper clusterItemMapper,
            ObjectMapper objectMapper
    ) {
        this.delegate = delegate;
        this.clusterItemMapper = clusterItemMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String version() {
        return RuleBasedClusterService.RULE_VERSION;
    }

    @Override
    public ClusterAssignmentResult assign(HotItemEntity item) {
        HotClusterEntity cluster = delegate.assign(item);
        HotClusterItemEntity membership = findActiveMembership(item.getId());
        if (membership == null) {
            // Defensive: delegate.assign always creates a membership, but if a
            // concurrent cleanup removed it we still want a deterministic
            // result rather than an NPE.
            return ClusterAssignmentResult.builder()
                    .cluster(cluster)
                    .decision(AssignmentDecision.NO_CANDIDATE)
                    .matchMethod("UNKNOWN")
                    .matchScore(BigDecimal.ONE)
                    .matchReason(simpleReason("UNKNOWN", item.getSourceUrl()))
                    .ruleVersion(version())
                    .build();
        }

        String matchMethod = Objects.requireNonNullElse(membership.getMatchMethod(), "UNKNOWN");
        BigDecimal score = membership.getMatchScore() == null
                ? BigDecimal.ONE
                : membership.getMatchScore();
        JsonNode reason = membership.getMatchReason() == null
                ? simpleReason(matchMethod, item.getSourceUrl())
                : membership.getMatchReason();

        return ClusterAssignmentResult.builder()
                .cluster(cluster)
                .decision(inferDecision(matchMethod))
                .matchMethod(matchMethod)
                .matchScore(score)
                .matchReason(reason)
                .ruleVersion(version())
                .build();
    }

    private AssignmentDecision inferDecision(String matchMethod) {
        // V1 only produces SINGLETON and CANONICAL_URL today. SINGLETON means
        // no candidate was found, so the decision is NO_CANDIDATE. Anything
        // else means the item was merged into an existing cluster, which is
        // ACCEPTED.
        if (MATCH_METHOD_SINGLETON.equals(matchMethod)) {
            return AssignmentDecision.NO_CANDIDATE;
        }
        return AssignmentDecision.ACCEPTED;
    }

    private HotClusterItemEntity findActiveMembership(long itemId) {
        return clusterItemMapper.selectOne(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotItemId, itemId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
    }

    private JsonNode simpleReason(String method, String url) {
        ObjectNode reason = objectMapper.createObjectNode();
        reason.put("method", method);
        if (url != null) {
            reason.put("canonicalUrl", url);
        }
        return reason;
    }
}
