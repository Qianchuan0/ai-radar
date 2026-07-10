package com.airadar.evaluation.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.entity.EvaluationCaseResultEntity;
import com.airadar.evaluation.entity.EvaluationDatasetEntity;
import com.airadar.evaluation.entity.EvaluationRunEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.mapper.EvaluationCaseResultMapper;
import com.airadar.evaluation.mapper.EvaluationDatasetMapper;
import com.airadar.evaluation.mapper.EvaluationRunMapper;
import com.airadar.evaluation.model.EvaluationCaseStatus;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.evaluation.model.EvaluationRunStatus;
import com.airadar.evaluation.service.verifier.CaseVerifier;
import com.airadar.evaluation.service.verifier.VerificationOutcome;
import com.airadar.evaluation.vo.EvaluationRunGenerationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationCaseMapper caseMapper;
    private final EvaluationRunMapper runMapper;
    private final EvaluationCaseResultMapper caseResultMapper;
    private final ObjectMapper objectMapper;
    private final Map<EvaluationCaseType, CaseVerifier> verifiers;

    public EvaluationRunner(
            EvaluationDatasetMapper datasetMapper,
            EvaluationCaseMapper caseMapper,
            EvaluationRunMapper runMapper,
            EvaluationCaseResultMapper caseResultMapper,
            ObjectMapper objectMapper,
            List<CaseVerifier> verifierBeans
    ) {
        this.datasetMapper = datasetMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.caseResultMapper = caseResultMapper;
        this.objectMapper = objectMapper;
        Map<EvaluationCaseType, CaseVerifier> registry = new EnumMap<>(EvaluationCaseType.class);
        for (CaseVerifier verifier : verifierBeans) {
            registry.put(verifier.supportedType(), verifier);
        }
        this.verifiers = registry;
    }

    @Transactional
    public EvaluationRunGenerationVO run(long datasetId) {
        EvaluationDatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException(ErrorCode.EVALUATION_DATASET_NOT_FOUND);
        }

        List<EvaluationCaseEntity> cases = caseMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseEntity>()
                        .eq(EvaluationCaseEntity::getDatasetId, datasetId)
                        .eq(EvaluationCaseEntity::getEnabled, Boolean.TRUE)
                        .orderByAsc(EvaluationCaseEntity::getId)
        );
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.EVALUATION_EMPTY_DATASET);
        }

        Instant startedAt = Instant.now();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setDatasetId(datasetId);
        run.setStatus(EvaluationRunStatus.RUNNING);
        run.setTotalCases(cases.size());
        run.setPassedCases(0);
        run.setFailedCases(0);
        run.setErrorCases(0);
        run.setMetricsPayload(objectMapper.createObjectNode());
        run.setErrorAnalysisPayload(objectMapper.createObjectNode());
        run.setStartedAt(startedAt);
        run.setCreatedAt(startedAt);
        runMapper.insert(run);

        int passed = 0;
        int failed = 0;
        int errors = 0;
        Map<EvaluationCaseType, int[]> countsByType = new EnumMap<>(EvaluationCaseType.class);
        for (EvaluationCaseType type : EvaluationCaseType.values()) {
            countsByType.put(type, new int[]{0, 0, 0, 0});
        }
        ObjectNode errorAnalysis = objectMapper.createObjectNode();
        ArrayNode failedCasesArray = objectMapper.createArrayNode();
        ArrayNode errorCasesArray = objectMapper.createArrayNode();

        for (EvaluationCaseEntity caseEntity : cases) {
            VerificationOutcome outcome = evaluateSafely(caseEntity);
            EvaluationCaseStatus status = outcome.status();
            switch (status) {
                case PASSED -> passed++;
                case FAILED -> failed++;
                case ERROR -> errors++;
            }
            EvaluationCaseType type = caseEntity.getCaseType();
            int[] typeCounts = countsByType.get(type);
            typeCounts[0]++;
            if (status == EvaluationCaseStatus.PASSED) {
                typeCounts[1]++;
            } else if (status == EvaluationCaseStatus.FAILED) {
                typeCounts[2]++;
            } else {
                typeCounts[3]++;
            }
            if (status == EvaluationCaseStatus.FAILED) {
                failedCasesArray.add(buildCaseSummary(caseEntity, outcome));
            } else if (status == EvaluationCaseStatus.ERROR) {
                errorCasesArray.add(buildCaseSummary(caseEntity, outcome));
            }

            EvaluationCaseResultEntity result = new EvaluationCaseResultEntity();
            result.setRunId(run.getId());
            result.setCaseId(caseEntity.getId());
            result.setCaseCode(caseEntity.getCaseCode());
            result.setCaseType(caseEntity.getCaseType());
            result.setStatus(status);
            result.setActualPayload(ensureObjectPayload(outcome.actualPayload()));
            result.setFailureReason(truncate(outcome.failureReason(), 500));
            result.setEvaluatedAt(Instant.now());
            caseResultMapper.insert(result);
        }

        Instant finishedAt = Instant.now();
        run.setStatus(EvaluationRunStatus.COMPLETED);
        run.setPassedCases(passed);
        run.setFailedCases(failed);
        run.setErrorCases(errors);
        run.setMetricsPayload(buildMetrics(cases.size(), passed, failed, errors, countsByType));
        errorAnalysis.set("failedCases", failedCasesArray);
        errorAnalysis.set("errorCases", errorCasesArray);
        errorAnalysis.set("failedByCaseType", buildFailureByCaseType(countsByType, false));
        errorAnalysis.set("errorByCaseType", buildFailureByCaseType(countsByType, true));
        run.setErrorAnalysisPayload(errorAnalysis);
        run.setFinishedAt(finishedAt);
        runMapper.updateById(run);

        return new EvaluationRunGenerationVO(
                run.getId(),
                datasetId,
                EvaluationRunStatus.COMPLETED,
                cases.size(),
                passed,
                failed,
                errors,
                startedAt,
                finishedAt
        );
    }

    private VerificationOutcome evaluateSafely(EvaluationCaseEntity caseEntity) {
        CaseVerifier verifier = verifiers.get(caseEntity.getCaseType());
        if (verifier == null) {
            return VerificationOutcome.error(
                    "No verifier registered for case type " + caseEntity.getCaseType() + "."
            );
        }
        try {
            return verifier.verify(caseEntity);
        } catch (Exception ex) {
            log.warn("Evaluation case {} threw an unexpected error.", caseEntity.getCaseCode(), ex);
            return VerificationOutcome.error("Unexpected verifier error: " + ex.getMessage());
        }
    }

    private JsonNode buildCaseSummary(EvaluationCaseEntity caseEntity, VerificationOutcome outcome) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("caseId", caseEntity.getId());
        node.put("caseCode", caseEntity.getCaseCode());
        node.put("caseType", caseEntity.getCaseType().name());
        if (outcome.failureReason() != null) {
            node.put("failureReason", truncate(outcome.failureReason(), 400));
        }
        return node;
    }

    private JsonNode buildMetrics(
            int total,
            int passed,
            int failed,
            int errors,
            Map<EvaluationCaseType, int[]> countsByType
    ) {
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("totalCases", total);
        metrics.put("passedCases", passed);
        metrics.put("failedCases", failed);
        metrics.put("errorCases", errors);
        double passRate = total == 0 ? 0.0 : (double) passed / (double) total;
        metrics.put("passRate", Math.round(passRate * 10000.0) / 10000.0);
        ObjectNode byCaseType = objectMapper.createObjectNode();
        for (EvaluationCaseType type : countsByType.keySet()) {
            int[] typeCounts = countsByType.get(type);
            if (typeCounts[0] == 0) {
                continue;
            }
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("total", typeCounts[0]);
            entry.put("passed", typeCounts[1]);
            entry.put("failed", typeCounts[2]);
            entry.put("error", typeCounts[3]);
            byCaseType.set(type.name(), entry);
        }
        metrics.set("byCaseType", byCaseType);
        return metrics;
    }

    private JsonNode buildFailureByCaseType(Map<EvaluationCaseType, int[]> countsByType, boolean errorCounts) {
        ObjectNode node = objectMapper.createObjectNode();
        for (EvaluationCaseType type : countsByType.keySet()) {
            int[] typeCounts = countsByType.get(type);
            int value = errorCounts ? typeCounts[3] : typeCounts[2];
            if (value > 0) {
                node.put(type.name(), value);
            }
        }
        return node;
    }

    private JsonNode ensureObjectPayload(JsonNode payload) {
        if (payload != null && payload.isObject()) {
            return payload;
        }
        return objectMapper.createObjectNode();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
