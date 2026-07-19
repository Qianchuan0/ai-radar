package com.airadar.evaluation.service.verifier;

import com.airadar.evaluation.model.EvaluationCaseType;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validates the JSON shape of {@code target_payload} and
 * {@code expected_payload} for a specific {@link EvaluationCaseType} at case
 * creation or import time.
 *
 * <p>Unlike {@link CaseVerifier}, this interface performs only structural
 * validation; it never queries the database or computes verification outcomes.
 * Real-data sample imports call validators to fail fast on malformed
 * annotations before persisting a case. Phase 17A wires validators only for
 * the two new real-data case types; Phase 8 case types continue to rely on
 * {@link CaseVerifier} for runtime validation.
 */
public interface EvaluationPayloadValidator {

    EvaluationCaseType supportedType();

    /**
     * Throws {@code BusinessException} with {@code ErrorCode.INVALID_ARGUMENT}
     * when {@code target} or {@code expected} does not match the schema.
     */
    void validate(JsonNode target, JsonNode expected);
}
