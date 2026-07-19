package com.airadar.evaluation.service.verifier;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Validates {@link EvaluationCaseType#RANKING_RELEVANCE_EXPECTATION} payloads
 * used by Phase 17A real-data ranking evaluation.
 *
 * <p>Required {@code target_payload} shape:
 * <pre>
 * {
 *   "windowStart": "2026-07-18T00:00:00Z",
 *   "windowEnd": "2026-07-18T23:59:59Z",
 *   "clusterId": 8801,
 *   "query": null,
 *   "snapshotSource": "hot_cluster_at_window_end"
 * }
 * </pre>
 *
 * <p>Required {@code expected_payload} shape:
 * <pre>
 * { "relevance": "HIGHLY_RELEVANT", "isMajorEvent": true }
 * </pre>
 */
@Component
public class RankingRelevancePayloadValidator implements EvaluationPayloadValidator {

    private static final Set<String> ALLOWED_RELEVANCE = Set.of(
            "HIGHLY_RELEVANT", "RELEVANT", "MARGINALLY_RELEVANT", "NOISE"
    );

    @Override
    public EvaluationCaseType supportedType() {
        return EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION;
    }

    @Override
    public void validate(JsonNode target, JsonNode expected) {
        requireObject(target, "target_payload");
        requireObject(expected, "expected_payload");

        requireInstant(target, "windowStart", "target_payload.windowStart");
        requireInstant(target, "windowEnd", "target_payload.windowEnd");

        long clusterId = target.path("clusterId").asLong();
        if (clusterId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "target_payload.clusterId must be a positive long");
        }

        String relevance = textOrEmpty(expected, "relevance");
        if (!ALLOWED_RELEVANCE.contains(relevance)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "expected_payload.relevance must be one of "
                            + ALLOWED_RELEVANCE + " (got '" + relevance + "')");
        }

        JsonNode major = expected.path("isMajorEvent");
        if (!major.isBoolean()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "expected_payload.isMajorEvent must be a boolean");
        }
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    path + " must be a JSON object");
        }
    }

    private static void requireInstant(JsonNode parent, String field, String path) {
        JsonNode value = parent.path(field);
        if (!value.isTextual()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    path + " must be an ISO-8601 string");
        }
        try {
            Instant.parse(value.asText());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    path + " is not a valid ISO-8601 instant: " + value.asText());
        }
    }

    private static String textOrEmpty(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        return value.isTextual() ? value.asText() : "";
    }
}
