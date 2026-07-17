package com.airadar.signal.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.signal.entity.SignalSnapshotEntity;
import com.airadar.signal.mapper.SignalSnapshotMapper;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.vo.SignalSnapshotVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SignalSnapshotService {

    private final SignalSnapshotMapper signalSnapshotMapper;
    private final HotItemMapper hotItemMapper;
    private final ObjectMapper objectMapper;

    public SignalSnapshotService(
        SignalSnapshotMapper signalSnapshotMapper,
        HotItemMapper hotItemMapper,
        ObjectMapper objectMapper
    ) {
        this.signalSnapshotMapper = signalSnapshotMapper;
        this.hotItemMapper = hotItemMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SignalSnapshotEntity save(RawItemEntity rawItem, HotItemEntity hotItem, NormalizedSignal signal) {
        SignalSnapshotEntity existing = findByRawItemId(rawItem.getId());
        if (existing != null) {
            return existing;
        }

        Instant now = Instant.now();
        SignalSnapshotEntity entity = new SignalSnapshotEntity();
        entity.setHotItemId(hotItem.getId());
        entity.setRawItemId(rawItem.getId());
        entity.setSourceType(signal.sourceType());
        entity.setSourceRole(signal.sourceRole());
        entity.setObservedAt(rawItem.getFetchedAt());
        entity.setRawMetrics(signal.rawMetrics());
        entity.setNormalizedSignal(objectMapper.valueToTree(signal));
        entity.setCreatedAt(now);

        try {
            signalSnapshotMapper.insert(entity);
            return entity;
        } catch (DuplicateKeyException exception) {
            SignalSnapshotEntity duplicate = findByRawItemId(rawItem.getId());
            if (duplicate != null) {
                return duplicate;
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<SignalSnapshotVO> listRecent(long hotItemId, int limit) {
        requireHotItem(hotItemId);
        return signalSnapshotMapper.selectList(
                new LambdaQueryWrapper<SignalSnapshotEntity>()
                    .eq(SignalSnapshotEntity::getHotItemId, hotItemId)
                    .orderByDesc(SignalSnapshotEntity::getObservedAt)
                    .orderByDesc(SignalSnapshotEntity::getId)
                    .last("LIMIT " + limit)
            ).stream()
            .map(this::toVO)
            .toList();
    }

    @Transactional(readOnly = true)
    public SignalSnapshotEntity latestSnapshot(long hotItemId) {
        requireHotItem(hotItemId);
        return signalSnapshotMapper.selectOne(
            new LambdaQueryWrapper<SignalSnapshotEntity>()
                .eq(SignalSnapshotEntity::getHotItemId, hotItemId)
                .orderByDesc(SignalSnapshotEntity::getObservedAt)
                .orderByDesc(SignalSnapshotEntity::getId)
                .last("LIMIT 1")
        );
    }

    @Transactional(readOnly = true)
    public List<SignalSnapshotEntity> listWithinWindow(long hotItemId, Instant from, Instant to) {
        requireHotItem(hotItemId);
        return signalSnapshotMapper.selectList(
            new LambdaQueryWrapper<SignalSnapshotEntity>()
                .eq(SignalSnapshotEntity::getHotItemId, hotItemId)
                .between(SignalSnapshotEntity::getObservedAt, from, to)
                .orderByAsc(SignalSnapshotEntity::getObservedAt)
                .orderByAsc(SignalSnapshotEntity::getId)
        );
    }

    @Transactional(readOnly = true)
    public HotItemEntity requireHotItem(long hotItemId) {
        HotItemEntity hotItem = hotItemMapper.selectById(hotItemId);
        if (hotItem == null) {
            throw new BusinessException(ErrorCode.HOT_ITEM_NOT_FOUND);
        }
        return hotItem;
    }

    private SignalSnapshotEntity findByRawItemId(Long rawItemId) {
        return signalSnapshotMapper.selectOne(
            new LambdaQueryWrapper<SignalSnapshotEntity>()
                .eq(SignalSnapshotEntity::getRawItemId, rawItemId)
                .last("LIMIT 1")
        );
    }

    private SignalSnapshotVO toVO(SignalSnapshotEntity entity) {
        return new SignalSnapshotVO(
            entity.getId(),
            entity.getHotItemId(),
            entity.getRawItemId(),
            entity.getSourceType(),
            entity.getSourceRole(),
            entity.getObservedAt(),
            entity.getRawMetrics(),
            entity.getNormalizedSignal(),
            entity.getCreatedAt()
        );
    }
}
