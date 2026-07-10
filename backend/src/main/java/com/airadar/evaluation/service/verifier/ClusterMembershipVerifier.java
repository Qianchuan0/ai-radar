package com.airadar.evaluation.service.verifier;

import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Verifies whether two normalized items are currently assigned to the same
 * {@code hot_cluster}.
 *
 * <p>Target payload:
 * {@code {"sourceTypeA": "...", "externalIdA": "...", "sourceTypeB": "...", "externalIdB": "..."}}.
 * Expected payload: {@code {"sameCluster": true|false}}.
 */
@Component
public class ClusterMembershipVerifier implements CaseVerifier {

    private final HotItemMapper hotItemMapper;
    private final HotClusterItemMapper hotClusterItemMapper;
    private final ObjectMapper objectMapper;

    public ClusterMembershipVerifier(
            HotItemMapper hotItemMapper,
            HotClusterItemMapper hotClusterItemMapper,
            ObjectMapper objectMapper
    ) {
        this.hotItemMapper = hotItemMapper;
        this.hotClusterItemMapper = hotClusterItemMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationCaseType supportedType() {
        return EvaluationCaseType.CLUSTER_MEMBERSHIP;
    }

    @Override
    public VerificationOutcome verify(EvaluationCaseEntity caseEntity) {
        JsonNode target = caseEntity.getTargetPayload();
        JsonNode expected = caseEntity.getExpectedPayload();

        Long itemA = resolveHotItemId(target, "A");
        Long itemB = resolveHotItemId(target, "B");
        if (itemA == null || itemB == null) {
            return VerificationOutcome.error(
                    "CLUSTER_MEMBERSHIP requires target.sourceTypeA/externalIdA and sourceTypeB/externalIdB."
            );
        }
        if (!expected.has("sameCluster") || !expected.get("sameCluster").isBoolean()) {
            return VerificationOutcome.error(
                    "CLUSTER_MEMBERSHIP requires boolean expected.sameCluster."
            );
        }
        boolean expectedSame = expected.path("sameCluster").asBoolean();

        Long clusterA = currentClusterId(itemA);
        Long clusterB = currentClusterId(itemB);

        ObjectNode actual = objectMapper.createObjectNode();
        actual.put("hotItemIdA", itemA);
        actual.put("hotItemIdB", itemB);
        putCluster(actual, "clusterIdA", clusterA);
        putCluster(actual, "clusterIdB", clusterB);

        if (clusterA == null || clusterB == null) {
            actual.put("sameCluster", false);
            return VerificationOutcome.failed(
                    actual,
                    "One or both items are not currently assigned to an active cluster."
            );
        }

        boolean actuallySame = clusterA.equals(clusterB);
        actual.put("sameCluster", actuallySame);

        if (actuallySame == expectedSame) {
            return VerificationOutcome.passed(actual);
        }
        return VerificationOutcome.failed(
                actual,
                "Expected items to be " + (expectedSame ? "in the same cluster" : "in different clusters")
                        + " but they were " + (actuallySame ? "together" : "separate") + "."
        );
    }

    private Long resolveHotItemId(JsonNode target, String suffix) {
        String sourceTypeRaw = target.path("sourceType" + suffix).asText();
        String externalId = target.path("externalId" + suffix).asText();
        if (sourceTypeRaw.isBlank() || externalId.isBlank()) {
            return null;
        }
        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(sourceTypeRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        List<HotItemEntity> matches = hotItemMapper.selectList(
                new LambdaQueryWrapper<HotItemEntity>()
                        .eq(HotItemEntity::getSourceType, sourceType)
                        .eq(HotItemEntity::getExternalId, externalId)
                        .last("LIMIT 1")
        );
        return matches.isEmpty() ? null : matches.get(0).getId();
    }

    private Long currentClusterId(Long hotItemId) {
        List<HotClusterItemEntity> assignments = hotClusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotItemId, hotItemId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
                        .last("LIMIT 1")
        );
        return assignments.isEmpty() ? null : assignments.get(0).getHotClusterId();
    }

    private void putCluster(ObjectNode node, String field, Long clusterId) {
        if (clusterId != null) {
            node.put(field, clusterId);
        } else {
            node.putNull(field);
        }
    }
}
