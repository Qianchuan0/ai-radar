package com.airadar.evaluation.service.verifier;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates {@link EvaluationCaseType#CLUSTER_PAIR_EXPECTATION} payloads used
 * by Phase 17A real-data cluster evaluation.
 *
 * <p>Required {@code target_payload} shape:
 * <pre>
 * {
 *   "pairKey": "openai-gpt5-39241938-2507.12345",
 *   "itemA": { "hotItemId": 12345, "sourceType": "HACKER_NEWS", "externalId": "39241938", "title": "..." },
 *   "itemB": { "hotItemId": 12347, "sourceType": "ARXIV", "externalId": "2507.12345", "title": "..." }
 * }
 * </pre>
 *
 * <p>{@code hotItemId} is preferred when present. When it is missing,
 * {@code sourceType} + {@code externalId} must be supplied so the evaluator
 * can resolve the item at runtime.
 *
 * <p>Required {@code expected_payload} shape:
 * <pre>
 * { "expectation": "MUST_MERGE", "category": "MODEL_RELEASE" }
 * </pre>
 */
@Component
public class ClusterPairPayloadValidator implements EvaluationPayloadValidator {

    private static final Set<String> ALLOWED_EXPECTATIONS = Set.of(
            "MUST_MERGE", "MUST_NOT_MERGE", "REVIEW_IF_AMBIGUOUS"
    );

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "MODEL_RELEASE", "VERSION_UPDATE", "API_PRICING",
            "OPEN_SOURCE_LAUNCH", "PAPER_RELEASE", "FUNDING",
            "ACQUISITION", "SECURITY_INCIDENT", "BENCHMARK",
            "SAME_NAME_DIFFERENT_EVENT", "SAME_COMPANY_DIFFERENT_EVENT", "OTHER"
    );

    @Override
    public EvaluationCaseType supportedType() {
        return EvaluationCaseType.CLUSTER_PAIR_EXPECTATION;
    }

    @Override
    public void validate(JsonNode target, JsonNode expected) {
        requireObject(target, "target_payload");
        requireObject(expected, "expected_payload");

        requireNonBlankText(target, "pairKey", "target_payload.pairKey");

        JsonNode itemA = target.path("itemA");
        JsonNode itemB = target.path("itemB");
        requireItem(itemA, "target_payload.itemA");
        requireItem(itemB, "target_payload.itemB");

        String expectation = textOrEmpty(expected, "expectation");
        if (!ALLOWED_EXPECTATIONS.contains(expectation)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "expected_payload.expectation must be one of "
                            + ALLOWED_EXPECTATIONS + " (got '" + expectation + "')");
        }

        String category = textOrEmpty(expected, "category");
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "expected_payload.category must be one of "
                            + ALLOWED_CATEGORIES + " (got '" + category + "')");
        }
    }

    private static void requireItem(JsonNode item, String path) {
        requireObject(item, path);
        long hotItemId = item.path("hotItemId").asLong();
        String sourceType = textOrEmpty(item, "sourceType");
        String externalId = textOrEmpty(item, "externalId");
        if (hotItemId <= 0 && (sourceType.isBlank() || externalId.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    path + " must contain either hotItemId or (sourceType, externalId)");
        }
        requireNonBlankText(item, "title", path + ".title");
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    path + " must be a JSON object");
        }
    }

    private static void requireNonBlankText(JsonNode parent, String field, String path) {
        JsonNode value = parent.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    path + " must be a non-blank string");
        }
    }

    private static String textOrEmpty(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        return value.isTextual() ? value.asText() : "";
    }
}
