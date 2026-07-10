package com.airadar.subscription.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.source.model.SourceType;
import com.airadar.subscription.dto.CreateSubscriptionRequest;
import com.airadar.subscription.entity.SubscriptionRuleEntity;
import com.airadar.subscription.mapper.SubscriptionRuleMapper;
import com.airadar.subscription.vo.SubscriptionRuleVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SubscriptionService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SourceType>> SOURCE_TYPE_LIST = new TypeReference<>() {
    };

    private final SubscriptionRuleMapper subscriptionRuleMapper;
    private final ObjectMapper objectMapper;

    public SubscriptionService(SubscriptionRuleMapper subscriptionRuleMapper, ObjectMapper objectMapper) {
        this.subscriptionRuleMapper = subscriptionRuleMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubscriptionRuleVO create(CreateSubscriptionRequest request) {
        List<String> keywords = normalizeKeywords(request.keywords());
        List<SourceType> sourceTypes = normalizeSourceTypes(request.sourceTypes());
        SubscriptionRuleEntity entity = new SubscriptionRuleEntity();
        entity.setName(request.name().trim());
        entity.setEnabled(request.enabled());
        entity.setKeywords(objectMapper.valueToTree(keywords));
        entity.setSourceTypes(objectMapper.valueToTree(sourceTypes));
        entity.setMinScore(request.minScore());
        entity.setSuppressWindowHours(request.suppressWindowHours());
        entity.setVersion(0);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            subscriptionRuleMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_ALREADY_EXISTS);
        }
        return toVO(entity);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRuleVO> list() {
        return subscriptionRuleMapper.selectList(new LambdaQueryWrapper<SubscriptionRuleEntity>()
                        .orderByAsc(SubscriptionRuleEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRuleEntity> listEnabledEntities() {
        return subscriptionRuleMapper.selectList(new LambdaQueryWrapper<SubscriptionRuleEntity>()
                .eq(SubscriptionRuleEntity::getEnabled, true)
                .orderByAsc(SubscriptionRuleEntity::getId));
    }

    @Transactional
    public SubscriptionRuleVO updateStatus(long subscriptionId, boolean enabled) {
        SubscriptionRuleEntity existing = findRequiredEntity(subscriptionId);
        LambdaUpdateWrapper<SubscriptionRuleEntity> update = new LambdaUpdateWrapper<>();
        update.eq(SubscriptionRuleEntity::getId, subscriptionId)
                .eq(SubscriptionRuleEntity::getVersion, existing.getVersion())
                .set(SubscriptionRuleEntity::getEnabled, enabled)
                .set(SubscriptionRuleEntity::getUpdatedAt, Instant.now())
                .setSql("version = version + 1");
        if (subscriptionRuleMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Subscription rule was updated concurrently.");
        }
        return toVO(findRequiredEntity(subscriptionId));
    }

    @Transactional(readOnly = true)
    public SubscriptionRuleEntity findRequiredEntity(long subscriptionId) {
        SubscriptionRuleEntity entity = subscriptionRuleMapper.selectById(subscriptionId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public SubscriptionRuleVO findRequired(long subscriptionId) {
        return toVO(findRequiredEntity(subscriptionId));
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        List<String> normalized = keywords.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Subscription keywords must contain at least one value.");
        }
        return normalized;
    }

    private List<SourceType> normalizeSourceTypes(List<SourceType> sourceTypes) {
        return List.copyOf(new LinkedHashSet<>(sourceTypes));
    }

    private SubscriptionRuleVO toVO(SubscriptionRuleEntity entity) {
        return new SubscriptionRuleVO(
                entity.getId(),
                entity.getName(),
                Boolean.TRUE.equals(entity.getEnabled()),
                readStringList(entity.getKeywords()),
                readSourceTypes(entity.getSourceTypes()),
                entity.getMinScore(),
                entity.getSuppressWindowHours(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<String> readStringList(JsonNode value) {
        return objectMapper.convertValue(value, STRING_LIST_TYPE);
    }

    private List<SourceType> readSourceTypes(JsonNode value) {
        return objectMapper.convertValue(value, SOURCE_TYPE_LIST);
    }
}
