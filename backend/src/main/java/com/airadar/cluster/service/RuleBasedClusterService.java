package com.airadar.cluster.service;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class RuleBasedClusterService {

    public static final String RULE_VERSION = "hn-rule-v1";

    private final HotClusterMapper hotClusterMapper;
    private final HotClusterItemMapper hotClusterItemMapper;
    private final HotItemMapper hotItemMapper;
    private final ObjectMapper objectMapper;

    public RuleBasedClusterService(
            HotClusterMapper hotClusterMapper,
            HotClusterItemMapper hotClusterItemMapper,
            HotItemMapper hotItemMapper,
            ObjectMapper objectMapper
    ) {
        this.hotClusterMapper = hotClusterMapper;
        this.hotClusterItemMapper = hotClusterItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.objectMapper = objectMapper;
    }

    public HotClusterEntity assign(HotItemEntity item) {
        HotClusterItemEntity currentMembership = findActiveMembership(item.getId());
        if (currentMembership != null) {
            HotClusterEntity cluster = hotClusterMapper.selectById(currentMembership.getHotClusterId());
            touchCluster(cluster, item.getLastSeenAt());
            return cluster;
        }

        HotClusterEntity canonicalUrlCluster = findClusterByCanonicalUrl(item);
        if (canonicalUrlCluster != null) {
            addMembership(canonicalUrlCluster, item, "CANONICAL_URL", false);
            touchCluster(canonicalUrlCluster, item.getLastSeenAt());
            return canonicalUrlCluster;
        }
        return createSingletonCluster(item);
    }

    private HotClusterEntity findClusterByCanonicalUrl(HotItemEntity item) {
        List<HotItemEntity> sameUrlItems = hotItemMapper.selectList(
                new LambdaQueryWrapper<HotItemEntity>()
                        .eq(HotItemEntity::getSourceUrl, item.getSourceUrl())
                        .ne(HotItemEntity::getId, item.getId())
                        .orderByAsc(HotItemEntity::getId)
        );
        for (HotItemEntity sameUrlItem : sameUrlItems) {
            HotClusterItemEntity membership = findActiveMembership(sameUrlItem.getId());
            if (membership != null) {
                HotClusterEntity cluster = hotClusterMapper.selectById(membership.getHotClusterId());
                if (cluster != null && "ACTIVE".equals(cluster.getStatus())) {
                    return cluster;
                }
            }
        }
        return null;
    }

    private HotClusterEntity createSingletonCluster(HotItemEntity item) {
        Instant now = Instant.now();
        HotClusterEntity cluster = new HotClusterEntity();
        cluster.setTitle(item.getTitle());
        cluster.setSummary(item.getSummary());
        cluster.setStatus("ACTIVE");
        cluster.setPrimaryItemId(item.getId());
        cluster.setFirstSeenAt(item.getFirstSeenAt());
        cluster.setLastSeenAt(item.getLastSeenAt());
        cluster.setVersion(0);
        cluster.setCreatedAt(now);
        cluster.setUpdatedAt(now);
        hotClusterMapper.insert(cluster);
        addMembership(cluster, item, "SINGLETON", true);
        return cluster;
    }

    private void addMembership(
            HotClusterEntity cluster,
            HotItemEntity item,
            String matchMethod,
            boolean primary
    ) {
        ObjectNode reason = objectMapper.createObjectNode();
        reason.put("method", matchMethod);
        reason.put("canonicalUrl", item.getSourceUrl());

        HotClusterItemEntity membership = new HotClusterItemEntity();
        membership.setHotClusterId(cluster.getId());
        membership.setHotItemId(item.getId());
        membership.setMatchMethod(matchMethod);
        membership.setMatchScore(BigDecimal.ONE);
        membership.setMatchReason(reason);
        membership.setRuleVersion(RULE_VERSION);
        membership.setIsPrimary(primary);
        membership.setAssignedAt(Instant.now());
        hotClusterItemMapper.insert(membership);
    }

    private HotClusterItemEntity findActiveMembership(long itemId) {
        return hotClusterItemMapper.selectOne(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotItemId, itemId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
    }

    private void touchCluster(HotClusterEntity cluster, Instant lastSeenAt) {
        if (cluster == null) {
            return;
        }
        if (lastSeenAt != null && (cluster.getLastSeenAt() == null || lastSeenAt.isAfter(cluster.getLastSeenAt()))) {
            cluster.setLastSeenAt(lastSeenAt);
        }
        cluster.setUpdatedAt(Instant.now());
        cluster.setVersion(cluster.getVersion() == null ? 1 : cluster.getVersion() + 1);
        hotClusterMapper.updateById(cluster);
    }
}
