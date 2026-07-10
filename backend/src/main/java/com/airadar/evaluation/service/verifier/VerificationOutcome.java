package com.airadar.evaluation.service.verifier;

import com.airadar.evaluation.model.EvaluationCaseStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Outcome of evaluating one evaluation case.
 *
 * <p>Status semantics:
 * <ul>
 *   <li>{@code PASSED} - the assertion held.</li>
 *   <li>{@code FAILED} - the verifier ran successfully but the assertion did not hold.</li>
 *   <li>{@code ERROR} - the verifier could not run, typically because a referenced
 *       entity is missing or the labeled payload is invalid.</li>
 * </ul>
 */
public record VerificationOutcome(
        EvaluationCaseStatus status,
        JsonNode actualPayload,
        String failureReason
) {

    public static VerificationOutcome passed(JsonNode actualPayload) {
        return new VerificationOutcome(EvaluationCaseStatus.PASSED, actualPayload, null);
    }

    public static VerificationOutcome failed(JsonNode actualPayload, String reason) {
        return new VerificationOutcome(EvaluationCaseStatus.FAILED, actualPayload, reason);
    }

    public static VerificationOutcome error(String reason) {
        return new VerificationOutcome(EvaluationCaseStatus.ERROR, NullNode.getInstance(), reason);
    }
}
