package com.airadar.evaluation.service.verifier;

import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.model.EvaluationCaseType;

/**
 * Verifies one {@link EvaluationCaseType}. Implementations must be stateless and
 * safe to invoke synchronously inside an evaluation run.
 */
public interface CaseVerifier {

    EvaluationCaseType supportedType();

    VerificationOutcome verify(EvaluationCaseEntity caseEntity);
}
