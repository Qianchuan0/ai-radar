package com.airadar.raw.service;

import com.airadar.crawl.collector.CollectedItem;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.mapper.RawItemMapper;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class RawItemService {

    private final RawItemMapper rawItemMapper;

    public RawItemService(RawItemMapper rawItemMapper) {
        this.rawItemMapper = rawItemMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RawItemEntity save(long taskId, SourceType sourceType, CollectedItem collectedItem) {
        RawItemEntity entity = new RawItemEntity();
        entity.setCrawlTaskId(taskId);
        entity.setSourceType(sourceType);
        entity.setExternalId(collectedItem.externalId());
        entity.setSourceUrl(collectedItem.sourceUrl());
        entity.setRawPayload(collectedItem.rawPayload());
        entity.setPayloadHash(sha256(collectedItem.rawPayload().toString()));
        entity.setPublishedAt(collectedItem.publishedAt());
        entity.setFetchedAt(collectedItem.fetchedAt());
        entity.setCreatedAt(Instant.now());
        rawItemMapper.insert(entity);
        return entity;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
