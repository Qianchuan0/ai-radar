package com.airadar.analysis.service;

import com.airadar.analysis.client.AnalysisProviderException;
import com.airadar.analysis.client.ClusterEvidencePack;
import com.airadar.analysis.client.StructuredAnalysisModelClient;
import com.airadar.analysis.entity.ClusterAnalysisEntity;
import com.airadar.analysis.mapper.ClusterAnalysisMapper;
import com.airadar.analysis.model.AnalysisRunStatus;
import com.airadar.analysis.model.AnalysisType;
import com.airadar.analysis.vo.ClusterAnalysisVO;
import com.airadar.analysis.vo.StructuredAnalysisResultVO;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.cluster.vo.HotItemEvidenceVO;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class AnalysisService {

    private static final AnalysisType DEFAULT_ANALYSIS_TYPE = AnalysisType.CLUSTER_BRIEF;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

    private final ClusterAnalysisMapper clusterAnalysisMapper;
    private final HotClusterQueryService hotClusterQueryService;
    private final StructuredAnalysisModelClient modelClient;
    private final AnalysisProperties analysisProperties;
    private final ObjectMapper objectMapper;

    public AnalysisService(
            ClusterAnalysisMapper clusterAnalysisMapper,
            HotClusterQueryService hotClusterQueryService,
            StructuredAnalysisModelClient modelClient,
            AnalysisProperties analysisProperties,
            ObjectMapper objectMapper
    ) {
        this.clusterAnalysisMapper = clusterAnalysisMapper;
        this.hotClusterQueryService = hotClusterQueryService;
        this.modelClient = modelClient;
        this.analysisProperties = analysisProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClusterAnalysisVO triggerLatest(long clusterId) {
        HotClusterDetailVO clusterDetail = hotClusterQueryService.get(clusterId);
        ClusterEvidencePack evidencePack = toEvidencePack(clusterDetail);
        String inputHash = hashEvidencePack(evidencePack);

        Instant startedAt = Instant.now();
        ClusterAnalysisEntity entity = new ClusterAnalysisEntity();
        entity.setHotClusterId(clusterId);
        entity.setAnalysisType(DEFAULT_ANALYSIS_TYPE);
        entity.setStatus(AnalysisRunStatus.RUNNING);
        entity.setSchemaVersion(analysisProperties.getSchemaVersion());
        entity.setPromptVersion(analysisProperties.getPromptVersion());
        entity.setModelProvider(analysisProperties.getProvider());
        entity.setModelName(analysisProperties.getModelName());
        entity.setInputHash(inputHash);
        entity.setStartedAt(startedAt);
        entity.setUpdatedAt(startedAt);
        clusterAnalysisMapper.insert(entity);

        try {
            StructuredAnalysisResultVO result = modelClient.analyze(evidencePack);
            validateResult(result);
            Instant finishedAt = Instant.now();
            entity.setStatus(AnalysisRunStatus.SUCCEEDED);
            entity.setResultPayload(objectMapper.valueToTree(result));
            entity.setFailureCode(null);
            entity.setFailureMessage(null);
            entity.setFinishedAt(finishedAt);
            entity.setUpdatedAt(finishedAt);
            clusterAnalysisMapper.updateById(entity);
            return toVO(entity);
        } catch (AnalysisProviderException ex) {
            Instant finishedAt = Instant.now();
            entity.setStatus(AnalysisRunStatus.FAILED);
            entity.setFailureCode(ex.errorCode().name());
            entity.setFailureMessage(truncateFailureMessage(ex.getMessage()));
            entity.setFinishedAt(finishedAt);
            entity.setUpdatedAt(finishedAt);
            clusterAnalysisMapper.updateById(entity);
            return toVO(entity);
        } catch (RuntimeException ex) {
            Instant finishedAt = Instant.now();
            entity.setStatus(AnalysisRunStatus.FAILED);
            entity.setFailureCode(ErrorCode.ANALYSIS_GENERATION_FAILED.name());
            entity.setFailureMessage(truncateFailureMessage(ex.getMessage()));
            entity.setFinishedAt(finishedAt);
            entity.setUpdatedAt(finishedAt);
            clusterAnalysisMapper.updateById(entity);
            return toVO(entity);
        }
    }

    @Transactional(readOnly = true)
    public ClusterAnalysisVO getLatest(long clusterId) {
        hotClusterQueryService.get(clusterId);
        ClusterAnalysisEntity entity = clusterAnalysisMapper.selectOne(
                new LambdaQueryWrapper<ClusterAnalysisEntity>()
                        .eq(ClusterAnalysisEntity::getHotClusterId, clusterId)
                        .orderByDesc(ClusterAnalysisEntity::getCreatedAt)
                        .orderByDesc(ClusterAnalysisEntity::getId)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new BusinessException(ErrorCode.CLUSTER_ANALYSIS_NOT_FOUND);
        }
        return toVO(entity);
    }

    private ClusterEvidencePack toEvidencePack(HotClusterDetailVO clusterDetail) {
        List<ClusterEvidencePack.EvidenceItemSnapshot> evidenceItems = clusterDetail.items().stream()
                .map(this::toEvidenceSnapshot)
                .toList();
        List<com.airadar.source.model.SourceType> sourceTypes = evidenceItems.stream()
                .map(ClusterEvidencePack.EvidenceItemSnapshot::sourceType)
                .distinct()
                .toList();
        return new ClusterEvidencePack(
                clusterDetail.id(),
                clusterDetail.title(),
                clusterDetail.summary(),
                clusterDetail.score() == null ? null : clusterDetail.score().total(),
                sourceTypes,
                evidenceItems,
                clusterDetail.firstSeenAt(),
                clusterDetail.lastSeenAt()
        );
    }

    private ClusterEvidencePack.EvidenceItemSnapshot toEvidenceSnapshot(HotItemEvidenceVO item) {
        return new ClusterEvidencePack.EvidenceItemSnapshot(
                item.id(),
                item.sourceType(),
                item.title(),
                item.summary(),
                item.sourceUrl(),
                item.author(),
                item.publishedAt()
        );
    }

    private String hashEvidencePack(ClusterEvidencePack evidencePack) {
        try {
            JsonNode payload = objectMapper.valueToTree(evidencePack);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private void validateResult(StructuredAnalysisResultVO result) {
        if (result == null) {
            throw new IllegalStateException("Structured analysis result is required.");
        }
        requireText(result.headline(), "headline");
        requireText(result.brief(), "brief");
        requireText(result.whyItMatters(), "whyItMatters");
        if (result.keySignals().isEmpty()) {
            throw new IllegalStateException("At least one key signal is required.");
        }
        if (result.evidenceRefs().isEmpty()) {
            throw new IllegalStateException("At least one evidence reference is required.");
        }
        requireText(result.confidence(), "confidence");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Structured analysis field " + fieldName + " is required.");
        }
    }

    private String truncateFailureMessage(String message) {
        String fallback = "Structured analysis generation failed.";
        String value = message == null || message.isBlank() ? fallback : message.trim();
        return value.length() <= MAX_FAILURE_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }

    private ClusterAnalysisVO toVO(ClusterAnalysisEntity entity) {
        StructuredAnalysisResultVO result = null;
        if (entity.getResultPayload() != null) {
            try {
                result = objectMapper.treeToValue(entity.getResultPayload(), StructuredAnalysisResultVO.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to map stored analysis payload.", ex);
            }
        }
        return new ClusterAnalysisVO(
                entity.getId(),
                entity.getHotClusterId(),
                entity.getAnalysisType(),
                entity.getStatus(),
                entity.getSchemaVersion(),
                entity.getPromptVersion(),
                entity.getModelProvider(),
                entity.getModelName(),
                entity.getInputHash(),
                result,
                entity.getFailureCode(),
                entity.getFailureMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt()
        );
    }
}
