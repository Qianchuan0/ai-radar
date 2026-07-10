package com.airadar.report.service;

import com.airadar.analysis.entity.ClusterAnalysisEntity;
import com.airadar.analysis.mapper.ClusterAnalysisMapper;
import com.airadar.analysis.model.AnalysisRunStatus;
import com.airadar.analysis.vo.ClusterAnalysisVO;
import com.airadar.analysis.vo.StructuredAnalysisResultVO;
import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.report.entity.DailyReportEntity;
import com.airadar.report.mapper.DailyReportMapper;
import com.airadar.report.model.ReportStatus;
import com.airadar.report.vo.DailyReportClusterVO;
import com.airadar.report.vo.DailyReportGenerationVO;
import com.airadar.report.vo.DailyReportSummaryVO;
import com.airadar.report.vo.DailyReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
public class DailyReportService {

    private static final int TOP_CLUSTER_LIMIT = 10;
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<DailyReportContentPayload> CONTENT_TYPE = new TypeReference<>() {
    };

    private final DailyReportMapper dailyReportMapper;
    private final HotClusterMapper hotClusterMapper;
    private final HotClusterQueryService hotClusterQueryService;
    private final ClusterAnalysisMapper clusterAnalysisMapper;
    private final ObjectMapper objectMapper;

    public DailyReportService(
            DailyReportMapper dailyReportMapper,
            HotClusterMapper hotClusterMapper,
            HotClusterQueryService hotClusterQueryService,
            ClusterAnalysisMapper clusterAnalysisMapper,
            ObjectMapper objectMapper
    ) {
        this.dailyReportMapper = dailyReportMapper;
        this.hotClusterMapper = hotClusterMapper;
        this.hotClusterQueryService = hotClusterQueryService;
        this.clusterAnalysisMapper = clusterAnalysisMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DailyReportGenerationVO generate(LocalDate reportDate) {
        Instant generatedAt = Instant.now();
        Instant from = reportDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = reportDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);

        long clusterCount = hotClusterMapper.countActive(null, from, to);
        List<HotClusterEntity> topClusters = hotClusterMapper.selectActivePage(
                null,
                from,
                to,
                HotClusterSort.SCORE_DESC.name(),
                TOP_CLUSTER_LIMIT,
                0
        );
        List<DailyReportClusterVO> clusterSnapshots = topClusters.stream()
                .map(cluster -> buildClusterSnapshot(cluster.getId()))
                .toList();
        List<Long> topClusterIds = clusterSnapshots.stream()
                .map(DailyReportClusterVO::hotClusterId)
                .toList();

        DailyReportContentPayload contentPayload = new DailyReportContentPayload(clusterSnapshots);
        DailyReportEntity entity = dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReportEntity>()
                .eq(DailyReportEntity::getReportDate, reportDate)
                .last("LIMIT 1"));
        boolean existing = entity != null;
        if (!existing) {
            entity = new DailyReportEntity();
            entity.setReportDate(reportDate);
            entity.setCreatedAt(generatedAt);
        }

        entity.setStatus(ReportStatus.GENERATED);
        entity.setTitle(buildTitle(reportDate));
        entity.setSummary(buildSummary(reportDate, clusterCount, clusterSnapshots));
        entity.setClusterCount(Math.toIntExact(clusterCount));
        entity.setTopClusterIds(objectMapper.valueToTree(topClusterIds));
        entity.setContentPayload(objectMapper.valueToTree(contentPayload));
        entity.setGeneratedAt(generatedAt);
        entity.setUpdatedAt(generatedAt);

        if (existing) {
            dailyReportMapper.updateById(entity);
        } else {
            dailyReportMapper.insert(entity);
        }

        return new DailyReportGenerationVO(reportDate, Math.toIntExact(clusterCount), generatedAt);
    }

    @Transactional(readOnly = true)
    public PageResponse<DailyReportSummaryVO> list(int page, int size) {
        long total = dailyReportMapper.selectCount(new LambdaQueryWrapper<DailyReportEntity>());
        long offset = (long) (page - 1) * size;
        List<DailyReportSummaryVO> items = dailyReportMapper.selectList(
                        new LambdaQueryWrapper<DailyReportEntity>()
                                .orderByDesc(DailyReportEntity::getReportDate)
                                .orderByDesc(DailyReportEntity::getId)
                                .last("LIMIT " + size + " OFFSET " + offset)
                ).stream()
                .map(this::toSummaryVO)
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public DailyReportVO get(LocalDate reportDate) {
        DailyReportEntity entity = dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReportEntity>()
                .eq(DailyReportEntity::getReportDate, reportDate)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        return toDetailVO(entity);
    }

    private DailyReportClusterVO buildClusterSnapshot(long clusterId) {
        HotClusterDetailVO detail = hotClusterQueryService.get(clusterId);
        return new DailyReportClusterVO(
                detail.id(),
                detail.title(),
                detail.summary(),
                detail.sourceTypes(),
                detail.itemCount(),
                detail.score(),
                detail.firstSeenAt(),
                detail.lastSeenAt(),
                latestAnalysis(clusterId)
        );
    }

    private ClusterAnalysisVO latestAnalysis(long clusterId) {
        ClusterAnalysisEntity entity = clusterAnalysisMapper.selectOne(
                new LambdaQueryWrapper<ClusterAnalysisEntity>()
                        .eq(ClusterAnalysisEntity::getHotClusterId, clusterId)
                        .eq(ClusterAnalysisEntity::getStatus, AnalysisRunStatus.SUCCEEDED)
                        .orderByDesc(ClusterAnalysisEntity::getCreatedAt)
                        .orderByDesc(ClusterAnalysisEntity::getId)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            return null;
        }
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

    private String buildTitle(LocalDate reportDate) {
        return "AI Radar Daily Report for " + reportDate;
    }

    private String buildSummary(
            LocalDate reportDate,
            long clusterCount,
            List<DailyReportClusterVO> clusterSnapshots
    ) {
        if (clusterCount == 0) {
            return "No active hot clusters were captured on " + reportDate + ".";
        }
        DailyReportClusterVO topCluster = clusterSnapshots.get(0);
        Set<String> sourceTypes = clusterSnapshots.stream()
                .flatMap(cluster -> cluster.sourceTypes().stream())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return "Tracked " + clusterCount + " active clusters on " + reportDate
                + ". Top cluster: " + topCluster.title()
                + ". Sources covered: " + String.join(", ", sourceTypes) + ".";
    }

    private DailyReportSummaryVO toSummaryVO(DailyReportEntity entity) {
        return new DailyReportSummaryVO(
                entity.getId(),
                entity.getReportDate(),
                entity.getStatus(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getClusterCount(),
                readTopClusterIds(entity.getTopClusterIds()),
                entity.getGeneratedAt()
        );
    }

    private DailyReportVO toDetailVO(DailyReportEntity entity) {
        DailyReportContentPayload payload = readContentPayload(entity.getContentPayload());
        return new DailyReportVO(
                entity.getId(),
                entity.getReportDate(),
                entity.getStatus(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getClusterCount(),
                readTopClusterIds(entity.getTopClusterIds()),
                payload.clusters(),
                entity.getGeneratedAt(),
                entity.getCreatedAt()
        );
    }

    private List<Long> readTopClusterIds(JsonNode value) {
        return value == null ? List.of() : objectMapper.convertValue(value, LONG_LIST_TYPE);
    }

    private DailyReportContentPayload readContentPayload(JsonNode value) {
        if (value == null) {
            return new DailyReportContentPayload(List.of());
        }
        return objectMapper.convertValue(value, CONTENT_TYPE);
    }

    private record DailyReportContentPayload(List<DailyReportClusterVO> clusters) {

        private DailyReportContentPayload {
            clusters = List.copyOf(clusters);
        }
    }
}
