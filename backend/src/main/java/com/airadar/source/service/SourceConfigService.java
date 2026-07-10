package com.airadar.source.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.airadar.source.model.SourceType;
import com.airadar.source.vo.SourceConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SourceConfigService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SourceConfigMapper sourceConfigMapper;
    private final ObjectMapper objectMapper;

    public SourceConfigService(SourceConfigMapper sourceConfigMapper, ObjectMapper objectMapper) {
        this.sourceConfigMapper = sourceConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SourceConfigVO create(CreateSourceRequest request) {
        validateConfig(request);
        SourceConfigEntity entity = new SourceConfigEntity();
        entity.setSourceCode(request.sourceCode());
        entity.setSourceType(request.sourceType());
        entity.setDisplayName(request.displayName());
        entity.setEnabled(request.enabled());
        entity.setCrawlIntervalMinutes(request.crawlIntervalMinutes());
        entity.setConfigPayload(objectMapper.valueToTree(request.config()));
        entity.setVersion(0);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            sourceConfigMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SOURCE_ALREADY_EXISTS);
        }
        return toVO(entity);
    }

    @Transactional(readOnly = true)
    public List<SourceConfigVO> list(SourceType sourceType, Boolean enabled) {
        LambdaQueryWrapper<SourceConfigEntity> query = new LambdaQueryWrapper<>();
        query.eq(sourceType != null, SourceConfigEntity::getSourceType, sourceType)
                .eq(enabled != null, SourceConfigEntity::getEnabled, enabled)
                .orderByAsc(SourceConfigEntity::getId);
        return sourceConfigMapper.selectList(query).stream().map(this::toVO).toList();
    }

    @Transactional
    public SourceConfigVO updateStatus(long sourceId, boolean enabled) {
        SourceConfigEntity existing = findRequired(sourceId);
        LambdaUpdateWrapper<SourceConfigEntity> update = new LambdaUpdateWrapper<>();
        update.eq(SourceConfigEntity::getId, sourceId)
                .eq(SourceConfigEntity::getVersion, existing.getVersion())
                .set(SourceConfigEntity::getEnabled, enabled)
                .set(SourceConfigEntity::getUpdatedAt, Instant.now())
                .setSql("version = version + 1");
        if (sourceConfigMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Source configuration was updated concurrently.");
        }
        return toVO(findRequired(sourceId));
    }

    @Transactional(readOnly = true)
    public SourceConfigEntity findRequired(long sourceId) {
        SourceConfigEntity entity = sourceConfigMapper.selectById(sourceId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SOURCE_NOT_FOUND);
        }
        return entity;
    }

    private SourceConfigVO toVO(SourceConfigEntity entity) {
        Map<String, Object> config = objectMapper.convertValue(entity.getConfigPayload(), MAP_TYPE);
        return new SourceConfigVO(
                entity.getId(),
                entity.getSourceCode(),
                entity.getSourceType(),
                entity.getDisplayName(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getCrawlIntervalMinutes(),
                config,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateConfig(CreateSourceRequest request) {
        JsonNode config = objectMapper.valueToTree(request.config());
        switch (request.sourceType()) {
            case HACKER_NEWS -> validateHackerNewsConfig(config);
            case ARXIV -> validateArxivConfig(config);
            case GITHUB -> validateGitHubConfig(config);
        }
    }

    private void validateHackerNewsConfig(JsonNode config) {
        String feed = config.path("feed").asText("TOP");
        if (!"TOP".equalsIgnoreCase(feed)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Hacker News feed must be TOP in Phase 2.");
        }
        int fetchLimit = config.path("fetchLimit").asInt(100);
        if (fetchLimit < 1 || fetchLimit > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Hacker News fetchLimit must be between 1 and 100."
            );
        }
        JsonNode keywords = config.path("keywords");
        if (!keywords.isArray() || keywords.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Hacker News keywords must contain at least one value."
            );
        }
    }

    private void validateArxivConfig(JsonNode config) {
        String searchQuery = config.path("searchQuery").asText("").trim();
        if (searchQuery.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "arXiv searchQuery must not be blank."
            );
        }
        int start = config.path("start").asInt(0);
        if (start < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "arXiv start must be zero or positive."
            );
        }
        int maxResults = config.path("maxResults").asInt(20);
        if (maxResults < 1 || maxResults > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "arXiv maxResults must be between 1 and 100."
            );
        }
    }

    private void validateGitHubConfig(JsonNode config) {
        String query = config.path("query").asText("").trim();
        if (query.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "GitHub query must not be blank."
            );
        }
        int perPage = config.path("perPage").asInt(10);
        if (perPage < 1 || perPage > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "GitHub perPage must be between 1 and 100."
            );
        }
        int page = config.path("page").asInt(1);
        if (page < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "GitHub page must be at least 1."
            );
        }
        if ((long) (page - 1) * perPage >= 1000) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "GitHub search pagination must stay within the first 1000 results."
            );
        }
        String sort = config.path("sort").asText("updated").trim();
        if (!sort.isBlank() && !"stars".equalsIgnoreCase(sort) && !"forks".equalsIgnoreCase(sort) && !"updated".equalsIgnoreCase(sort)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "GitHub sort must be one of stars, forks, updated."
            );
        }
        String order = config.path("order").asText("desc").trim();
        if (!order.isBlank() && !"asc".equalsIgnoreCase(order) && !"desc".equalsIgnoreCase(order)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "GitHub order must be asc or desc."
            );
        }
    }
}
