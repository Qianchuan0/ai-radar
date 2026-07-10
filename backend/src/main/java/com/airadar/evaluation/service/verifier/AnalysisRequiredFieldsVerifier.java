package com.airadar.evaluation.service.verifier;

import com.airadar.analysis.entity.ClusterAnalysisEntity;
import com.airadar.analysis.mapper.ClusterAnalysisMapper;
import com.airadar.analysis.model.AnalysisRunStatus;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that the latest succeeded {@code cluster_analysis} for a cluster
 * exists and contains the required structured-analysis fields.
 *
 * <p>Target payload: {@code {"hotClusterId": 1}}.
 * Expected payload: {@code {"fields": ["headline", "brief", "evidenceRefs", ...]}}.
 */
@Component
public class AnalysisRequiredFieldsVerifier implements CaseVerifier {

    private final ClusterAnalysisMapper clusterAnalysisMapper;
    private final ObjectMapper objectMapper;

    public AnalysisRequiredFieldsVerifier(
            ClusterAnalysisMapper clusterAnalysisMapper,
            ObjectMapper objectMapper
    ) {
        this.clusterAnalysisMapper = clusterAnalysisMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationCaseType supportedType() {
        return EvaluationCaseType.ANALYSIS_REQUIRED_FIELDS;
    }

    @Override
    public VerificationOutcome verify(EvaluationCaseEntity caseEntity) {
        JsonNode target = caseEntity.getTargetPayload();
        JsonNode expected = caseEntity.getExpectedPayload();

        long clusterId = target.path("hotClusterId").asLong(0L);
        if (clusterId <= 0L) {
            return VerificationOutcome.error(
                    "ANALYSIS_REQUIRED_FIELDS requires a positive target.hotClusterId."
            );
        }
        JsonNode fieldsNode = expected.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray() || fieldsNode.isEmpty()) {
            return VerificationOutcome.error(
                    "ANALYSIS_REQUIRED_FIELDS requires a non-empty expected.fields array."
            );
        }
        List<String> requiredFields = new ArrayList<>();
        fieldsNode.forEach(field -> requiredFields.add(field.asText()));

        List<ClusterAnalysisEntity> analyses = clusterAnalysisMapper.selectList(
                new LambdaQueryWrapper<ClusterAnalysisEntity>()
                        .eq(ClusterAnalysisEntity::getHotClusterId, clusterId)
                        .eq(ClusterAnalysisEntity::getStatus, AnalysisRunStatus.SUCCEEDED)
                        .orderByDesc(ClusterAnalysisEntity::getCreatedAt)
                        .orderByDesc(ClusterAnalysisEntity::getId)
                        .last("LIMIT 1")
        );
        if (analyses.isEmpty()) {
            return VerificationOutcome.error(
                    "No succeeded cluster_analysis found for cluster " + clusterId + "."
            );
        }
        ClusterAnalysisEntity analysis = analyses.get(0);
        JsonNode result = analysis.getResultPayload();

        ObjectNode actual = objectMapper.createObjectNode();
        actual.put("hotClusterId", clusterId);
        actual.put("analysisId", analysis.getId());
        actual.put("schemaVersion", analysis.getSchemaVersion());
        ArrayNode presentFields = objectMapper.createArrayNode();
        ArrayNode missingFields = objectMapper.createArrayNode();
        for (String field : requiredFields) {
            JsonNode value = result == null ? null : result.get(field);
            if (isPresent(value)) {
                presentFields.add(field);
            } else {
                missingFields.add(field);
            }
        }
        actual.set("presentFields", presentFields);
        actual.set("missingFields", missingFields);

        if (missingFields.isEmpty()) {
            return VerificationOutcome.passed(actual);
        }
        return VerificationOutcome.failed(
                actual,
                "Latest analysis is missing required fields: " + missingFields + "."
        );
    }

    private boolean isPresent(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText().isBlank();
        }
        if (value.isArray()) {
            return !value.isEmpty();
        }
        if (value.isObject()) {
            return !value.isEmpty();
        }
        return true;
    }
}
