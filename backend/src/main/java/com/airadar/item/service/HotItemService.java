package com.airadar.item.service;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.item.model.NormalizedHotItem;
import com.airadar.raw.entity.RawItemEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HotItemService {

    private final HotItemMapper hotItemMapper;

    public HotItemService(HotItemMapper hotItemMapper) {
        this.hotItemMapper = hotItemMapper;
    }

    public HotItemEntity upsert(RawItemEntity rawItem, NormalizedHotItem normalized) {
        HotItemEntity entity = hotItemMapper.selectOne(
                new LambdaQueryWrapper<HotItemEntity>()
                        .eq(HotItemEntity::getSourceType, rawItem.getSourceType())
                        .eq(HotItemEntity::getExternalId, rawItem.getExternalId())
        );
        Instant now = Instant.now();
        if (entity == null) {
            entity = new HotItemEntity();
            entity.setSourceType(rawItem.getSourceType());
            entity.setExternalId(rawItem.getExternalId());
            entity.setFirstSeenAt(rawItem.getFetchedAt());
            entity.setCreatedAt(now);
        }
        entity.setLatestRawItemId(rawItem.getId());
        entity.setItemType(normalized.itemType());
        entity.setTitle(normalized.title());
        entity.setSummary(normalized.summary());
        entity.setSourceUrl(normalized.sourceUrl());
        entity.setAuthor(normalized.author());
        entity.setTags(normalized.tags());
        entity.setMetrics(normalized.metrics());
        entity.setContentHash(normalized.contentHash());
        entity.setPublishedAt(normalized.publishedAt());
        entity.setLastSeenAt(rawItem.getFetchedAt());
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            hotItemMapper.insert(entity);
        } else {
            hotItemMapper.updateById(entity);
        }
        return entity;
    }
}
