package com.airadar.alert.service;

import com.airadar.alert.entity.AlertRecordEntity;
import com.airadar.alert.mapper.AlertRecordMapper;
import com.airadar.alert.model.AlertStatus;
import com.airadar.alert.vo.AlertMatchingRunVO;
import com.airadar.alert.vo.AlertRecordVO;
import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.scoring.strategy.ScoringStrategyProperties;
import com.airadar.subscription.entity.SubscriptionRuleEntity;
import com.airadar.subscription.service.SubscriptionService;
import com.airadar.subscription.vo.SubscriptionRuleVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AlertService {

    private static final int SCAN_PAGE_SIZE = 100;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AlertRecordMapper alertRecordMapper;
    private final SubscriptionService subscriptionService;
    private final HotClusterMapper hotClusterMapper;
    private final HotClusterQueryService hotClusterQueryService;
    private final ScoringStrategyProperties scoringProperties;
    private final ObjectMapper objectMapper;

    public AlertService(
            AlertRecordMapper alertRecordMapper,
            SubscriptionService subscriptionService,
            HotClusterMapper hotClusterMapper,
            HotClusterQueryService hotClusterQueryService,
            ScoringStrategyProperties scoringProperties,
            ObjectMapper objectMapper
    ) {
        this.alertRecordMapper = alertRecordMapper;
        this.subscriptionService = subscriptionService;
        this.hotClusterMapper = hotClusterMapper;
        this.hotClusterQueryService = hotClusterQueryService;
        this.scoringProperties = scoringProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AlertMatchingRunVO runMatching() {
        List<SubscriptionRuleEntity> rules = subscriptionService.listEnabledEntities();
        if (rules.isEmpty()) {
            return new AlertMatchingRunVO(0, 0, 0, 0, Instant.now());
        }

        long totalClusters = hotClusterMapper.countActive(null, null, null);
        int scannedClusterCount = 0;
        int matchedRuleCount = 0;
        int createdAlertCount = 0;
        int suppressedAlertCount = 0;

        for (long offset = 0; offset < totalClusters; offset += SCAN_PAGE_SIZE) {
            List<HotClusterEntity> clusters = hotClusterMapper.selectActivePage(
                    null,
                    null,
                    null,
                    HotClusterSort.LATEST.name(),
                    scoringProperties.effectiveOnlineVersion(),
                    SCAN_PAGE_SIZE,
                    offset
            );
            for (HotClusterEntity cluster : clusters) {
                scannedClusterCount++;
                HotClusterDetailVO detail = hotClusterQueryService.get(cluster.getId());
                for (SubscriptionRuleEntity rule : rules) {
                    MatchEvaluation match = evaluateMatch(rule, detail);
                    if (!match.matched()) {
                        continue;
                    }
                    matchedRuleCount++;
                    if (createAlertIfNeeded(rule, detail, match.matchReason())) {
                        createdAlertCount++;
                    } else {
                        suppressedAlertCount++;
                    }
                }
            }
        }

        return new AlertMatchingRunVO(
                scannedClusterCount,
                matchedRuleCount,
                createdAlertCount,
                suppressedAlertCount,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertRecordVO> list(int page, int size, Long subscriptionId, AlertStatus status) {
        long total = alertRecordMapper.selectCount(new LambdaQueryWrapper<AlertRecordEntity>()
                .eq(subscriptionId != null, AlertRecordEntity::getSubscriptionRuleId, subscriptionId)
                .eq(status != null, AlertRecordEntity::getStatus, status));
        long offset = (long) (page - 1) * size;
        List<AlertRecordVO> items = alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecordEntity>()
                        .eq(subscriptionId != null, AlertRecordEntity::getSubscriptionRuleId, subscriptionId)
                        .eq(status != null, AlertRecordEntity::getStatus, status)
                        .orderByDesc(AlertRecordEntity::getMatchedAt)
                        .orderByDesc(AlertRecordEntity::getId)
                        .last("LIMIT " + size + " OFFSET " + offset))
                .stream()
                .map(this::toVO)
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional
    public AlertRecordVO updateStatus(long alertId, AlertStatus status) {
        if (status == AlertStatus.NEW) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Alert status update must be ACKED or DISMISSED.");
        }
        AlertRecordEntity existing = findRequiredEntity(alertId);
        LambdaUpdateWrapper<AlertRecordEntity> update = new LambdaUpdateWrapper<>();
        update.eq(AlertRecordEntity::getId, alertId)
                .set(AlertRecordEntity::getStatus, status)
                .set(AlertRecordEntity::getUpdatedAt, Instant.now());
        alertRecordMapper.update(null, update);
        existing.setStatus(status);
        existing.setUpdatedAt(Instant.now());
        return toVO(findRequiredEntity(alertId));
    }

    private boolean createAlertIfNeeded(
            SubscriptionRuleEntity rule,
            HotClusterDetailVO detail,
            Map<String, Object> matchReason
    ) {
        Instant matchedAt = Instant.now();
        String suppressionKey = buildSuppressionKey(
                rule.getId(),
                detail.id(),
                matchedAt,
                rule.getSuppressWindowHours()
        );

        AlertRecordEntity entity = new AlertRecordEntity();
        entity.setSubscriptionRuleId(rule.getId());
        entity.setHotClusterId(detail.id());
        entity.setStatus(AlertStatus.NEW);
        entity.setMatchReason(objectMapper.valueToTree(matchReason));
        entity.setSuppressionKey(suppressionKey);
        entity.setMatchedAt(matchedAt);
        entity.setCreatedAt(matchedAt);
        entity.setUpdatedAt(matchedAt);
        try {
            alertRecordMapper.insert(entity);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private MatchEvaluation evaluateMatch(SubscriptionRuleEntity rule, HotClusterDetailVO detail) {
        SubscriptionRuleVO subscription = subscriptionService.findRequired(rule.getId());
        List<String> matchedKeywords = subscription.keywords().stream()
                .filter(keyword -> containsKeyword(detail, keyword))
                .toList();
        if (matchedKeywords.isEmpty()) {
            return MatchEvaluation.notMatched();
        }

        if (!subscription.sourceTypes().isEmpty()) {
            boolean sourceMatched = detail.sourceTypes().stream().anyMatch(subscription.sourceTypes()::contains);
            if (!sourceMatched) {
                return MatchEvaluation.notMatched();
            }
        }

        BigDecimal score = detail.score() == null
                ? BigDecimal.ZERO
                : detail.score().total();
        if (subscription.minScore() != null && score.compareTo(subscription.minScore()) < 0) {
            return MatchEvaluation.notMatched();
        }

        Map<String, Object> reason = Map.of(
                "matchedKeywords", matchedKeywords,
                "matchedSourceTypes", new LinkedHashSet<>(detail.sourceTypes()),
                "clusterTitle", detail.title(),
                "clusterScore", score,
                "itemCount", detail.itemCount()
        );
        return MatchEvaluation.matched(reason);
    }

    private boolean containsKeyword(HotClusterDetailVO detail, String keyword) {
        String value = keyword.toLowerCase(Locale.ROOT);
        if (safeText(detail.title()).contains(value) || safeText(detail.summary()).contains(value)) {
            return true;
        }
        return detail.items().stream().anyMatch(item ->
                safeText(item.title()).contains(value)
                        || safeText(item.summary()).contains(value)
                        || safeText(item.sourceUrl()).contains(value)
        );
    }

    private String safeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String buildSuppressionKey(long ruleId, long clusterId, Instant matchedAt, int suppressWindowHours) {
        long windowSeconds = suppressWindowHours * 3600L;
        long bucket = matchedAt.getEpochSecond() / windowSeconds;
        String raw = ruleId + ":" + clusterId + ":" + bucket;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private AlertRecordEntity findRequiredEntity(long alertId) {
        AlertRecordEntity entity = alertRecordMapper.selectById(alertId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.ALERT_NOT_FOUND);
        }
        return entity;
    }

    private AlertRecordVO toVO(AlertRecordEntity entity) {
        SubscriptionRuleVO subscription = subscriptionService.findRequired(entity.getSubscriptionRuleId());
        HotClusterDetailVO cluster = hotClusterQueryService.get(entity.getHotClusterId());
        BigDecimal score = cluster.score() == null ? null : cluster.score().total();
        return new AlertRecordVO(
                entity.getId(),
                entity.getSubscriptionRuleId(),
                subscription.name(),
                entity.getHotClusterId(),
                cluster.title(),
                cluster.sourceTypes(),
                score,
                entity.getStatus(),
                objectMapper.convertValue(entity.getMatchReason(), MAP_TYPE),
                entity.getMatchedAt(),
                entity.getCreatedAt()
        );
    }

    private record MatchEvaluation(boolean matched, Map<String, Object> matchReason) {

        private static MatchEvaluation matched(Map<String, Object> matchReason) {
            return new MatchEvaluation(true, matchReason);
        }

        private static MatchEvaluation notMatched() {
            return new MatchEvaluation(false, Map.of());
        }
    }
}
