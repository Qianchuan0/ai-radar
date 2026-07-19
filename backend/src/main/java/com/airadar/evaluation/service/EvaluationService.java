package com.airadar.evaluation.service;

import com.airadar.common.api.PageResponse;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.evaluation.dto.CreateEvaluationCaseRequest;
import com.airadar.evaluation.dto.CreateEvaluationDatasetRequest;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.entity.EvaluationCaseResultEntity;
import com.airadar.evaluation.entity.EvaluationDatasetEntity;
import com.airadar.evaluation.entity.EvaluationRunEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.mapper.EvaluationCaseResultMapper;
import com.airadar.evaluation.mapper.EvaluationDatasetMapper;
import com.airadar.evaluation.mapper.EvaluationRunMapper;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.evaluation.service.verifier.EvaluationPayloadValidator;
import com.airadar.evaluation.vo.EvaluationCaseResultVO;
import com.airadar.evaluation.vo.EvaluationCaseVO;
import com.airadar.evaluation.vo.EvaluationDatasetVO;
import com.airadar.evaluation.vo.EvaluationRunGenerationVO;
import com.airadar.evaluation.vo.EvaluationRunSummaryVO;
import com.airadar.evaluation.vo.EvaluationRunVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationService {

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationCaseMapper caseMapper;
    private final EvaluationRunMapper runMapper;
    private final EvaluationCaseResultMapper caseResultMapper;
    private final EvaluationRunner evaluationRunner;
    private final Map<EvaluationCaseType, EvaluationPayloadValidator> payloadValidators;

    public EvaluationService(
            EvaluationDatasetMapper datasetMapper,
            EvaluationCaseMapper caseMapper,
            EvaluationRunMapper runMapper,
            EvaluationCaseResultMapper caseResultMapper,
            EvaluationRunner evaluationRunner,
            List<EvaluationPayloadValidator> payloadValidatorBeans
    ) {
        this.datasetMapper = datasetMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.caseResultMapper = caseResultMapper;
        this.evaluationRunner = evaluationRunner;
        Map<EvaluationCaseType, EvaluationPayloadValidator> registry =
                new EnumMap<>(EvaluationCaseType.class);
        for (EvaluationPayloadValidator validator : payloadValidatorBeans) {
            registry.put(validator.supportedType(), validator);
        }
        this.payloadValidators = registry;
    }

    @Transactional
    public EvaluationDatasetVO createDataset(CreateEvaluationDatasetRequest request) {
        Instant now = Instant.now();
        EvaluationDatasetEntity entity = new EvaluationDatasetEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setVersion(1);
        entity.setEnabled(request.enabled());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        datasetMapper.insert(entity);
        return toDatasetVO(entity, 0);
    }

    @Transactional(readOnly = true)
    public List<EvaluationDatasetVO> listDatasets() {
        List<EvaluationDatasetEntity> datasets = datasetMapper.selectList(
                new LambdaQueryWrapper<EvaluationDatasetEntity>()
                        .orderByDesc(EvaluationDatasetEntity::getCreatedAt)
                        .orderByDesc(EvaluationDatasetEntity::getId)
        );
        Map<Long, Integer> caseCounts = countCasesByDataset(datasets);
        return datasets.stream()
                .map(entity -> toDatasetVO(entity, caseCounts.getOrDefault(entity.getId(), 0)))
                .toList();
    }

    @Transactional
    public EvaluationCaseVO createCase(long datasetId, CreateEvaluationCaseRequest request) {
        EvaluationDatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException(ErrorCode.EVALUATION_DATASET_NOT_FOUND);
        }
        EvaluationPayloadValidator validator = payloadValidators.get(request.caseType());
        if (validator != null) {
            validator.validate(request.targetPayload(), request.expectedPayload());
        }
        Instant now = Instant.now();
        EvaluationCaseEntity entity = new EvaluationCaseEntity();
        entity.setDatasetId(datasetId);
        entity.setCaseCode(request.caseCode());
        entity.setCaseType(request.caseType());
        entity.setTargetPayload(request.targetPayload());
        entity.setExpectedPayload(request.expectedPayload());
        entity.setNotes(request.notes());
        entity.setEnabled(request.enabled());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        caseMapper.insert(entity);
        return toCaseVO(entity);
    }

    @Transactional(readOnly = true)
    public List<EvaluationCaseVO> listCases(long datasetId) {
        if (datasetMapper.selectById(datasetId) == null) {
            throw new BusinessException(ErrorCode.EVALUATION_DATASET_NOT_FOUND);
        }
        return caseMapper.selectList(
                        new LambdaQueryWrapper<EvaluationCaseEntity>()
                                .eq(EvaluationCaseEntity::getDatasetId, datasetId)
                                .orderByAsc(EvaluationCaseEntity::getId)
                ).stream()
                .map(this::toCaseVO)
                .toList();
    }

    @Transactional
    public EvaluationRunGenerationVO triggerRun(long datasetId) {
        return evaluationRunner.run(datasetId);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationRunSummaryVO> listRuns(Long datasetId, int page, int size) {
        LambdaQueryWrapper<EvaluationRunEntity> countWrapper = new LambdaQueryWrapper<>();
        if (datasetId != null) {
            countWrapper.eq(EvaluationRunEntity::getDatasetId, datasetId);
        }
        long total = runMapper.selectCount(countWrapper);
        long offset = (long) (page - 1) * size;
        LambdaQueryWrapper<EvaluationRunEntity> listWrapper = new LambdaQueryWrapper<EvaluationRunEntity>()
                .orderByDesc(EvaluationRunEntity::getCreatedAt)
                .orderByDesc(EvaluationRunEntity::getId);
        if (datasetId != null) {
            listWrapper.eq(EvaluationRunEntity::getDatasetId, datasetId);
        }
        List<EvaluationRunSummaryVO> items = runMapper.selectList(
                        listWrapper.last("LIMIT " + size + " OFFSET " + offset)
                ).stream()
                .map(this::toSummaryVO)
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public EvaluationRunVO getRun(long runId) {
        EvaluationRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.EVALUATION_RUN_NOT_FOUND);
        }
        EvaluationDatasetEntity dataset = datasetMapper.selectById(run.getDatasetId());
        List<EvaluationCaseResultVO> results = caseResultMapper.selectList(
                        new LambdaQueryWrapper<EvaluationCaseResultEntity>()
                                .eq(EvaluationCaseResultEntity::getRunId, runId)
                                .orderByAsc(EvaluationCaseResultEntity::getId)
                ).stream()
                .map(this::toResultVO)
                .toList();
        return new EvaluationRunVO(
                run.getId(),
                run.getDatasetId(),
                dataset == null ? null : dataset.getName(),
                run.getStatus(),
                run.getTotalCases(),
                run.getPassedCases(),
                run.getFailedCases(),
                run.getErrorCases(),
                run.getMetricsPayload(),
                run.getErrorAnalysisPayload(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                results
        );
    }

    private Map<Long, Integer> countCasesByDataset(List<EvaluationDatasetEntity> datasets) {
        Map<Long, Integer> counts = new HashMap<>();
        if (datasets.isEmpty()) {
            return counts;
        }
        for (EvaluationDatasetEntity dataset : datasets) {
            long count = caseMapper.selectCount(
                    new LambdaQueryWrapper<EvaluationCaseEntity>()
                            .eq(EvaluationCaseEntity::getDatasetId, dataset.getId())
            );
            counts.put(dataset.getId(), (int) count);
        }
        return counts;
    }

    private EvaluationDatasetVO toDatasetVO(EvaluationDatasetEntity entity, int caseCount) {
        return new EvaluationDatasetVO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVersion() == null ? 0 : entity.getVersion(),
                Boolean.TRUE.equals(entity.getEnabled()),
                caseCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private EvaluationCaseVO toCaseVO(EvaluationCaseEntity entity) {
        return new EvaluationCaseVO(
                entity.getId(),
                entity.getDatasetId(),
                entity.getCaseCode(),
                entity.getCaseType(),
                entity.getTargetPayload(),
                entity.getExpectedPayload(),
                entity.getNotes(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private EvaluationRunSummaryVO toSummaryVO(EvaluationRunEntity entity) {
        EvaluationDatasetEntity dataset = entity.getDatasetId() == null
                ? null
                : datasetMapper.selectById(entity.getDatasetId());
        return new EvaluationRunSummaryVO(
                entity.getId(),
                entity.getDatasetId(),
                dataset == null ? null : dataset.getName(),
                entity.getStatus(),
                entity.getTotalCases(),
                entity.getPassedCases(),
                entity.getFailedCases(),
                entity.getErrorCases(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt()
        );
    }

    private EvaluationCaseResultVO toResultVO(EvaluationCaseResultEntity entity) {
        return new EvaluationCaseResultVO(
                entity.getId(),
                entity.getCaseId(),
                entity.getCaseCode(),
                entity.getCaseType(),
                entity.getStatus(),
                entity.getActualPayload(),
                entity.getFailureReason(),
                entity.getEvaluatedAt()
        );
    }
}
