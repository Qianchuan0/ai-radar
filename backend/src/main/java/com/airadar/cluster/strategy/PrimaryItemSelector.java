package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.signal.adapter.SourceSignalAdapterRegistry;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Re-selects the primary item for a cluster after V2 membership changes.
 *
 * <p>Priority follows the Phase 16 plan:
 * <pre>
 *   PRIMARY (official / arXiv / official repo / official model page)
 *   &gt; ADOPTION (GitHub, Hugging Face)
 *   &gt; MEDIA
 *   &gt; COMMUNITY (Hacker News, Weibo, Twitter)
 *   &gt; DISCOVERY (Bing, DuckDuckGo, Sogou)
 * </pre>
 *
 * <p>Ties on role are broken by item id (stable, deterministic).
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>{@code hot_cluster_item} has a unique partial index on
 *       {@code (hot_cluster_id) WHERE removed_at IS NULL AND is_primary}, so
 *       the selector first clears every active primary flag in the cluster
 *       before promoting the new one. Both writes happen in the caller's
 *       transaction.</li>
 *   <li>The selector is a no-op when the cluster already has the right
 *       primary, so it is cheap to call after every accepted assignment.</li>
 * </ul>
 */
@Component
public class PrimaryItemSelector {

    private static final Logger log = LoggerFactory.getLogger(PrimaryItemSelector.class);

    private final HotClusterItemMapper clusterItemMapper;
    private final HotClusterMapper clusterMapper;
    private final HotItemMapper hotItemMapper;
    private final SourceSignalAdapterRegistry signalAdapterRegistry;

    public PrimaryItemSelector(
            HotClusterItemMapper clusterItemMapper,
            HotClusterMapper clusterMapper,
            HotItemMapper hotItemMapper,
            SourceSignalAdapterRegistry signalAdapterRegistry
    ) {
        this.clusterItemMapper = clusterItemMapper;
        this.clusterMapper = clusterMapper;
        this.hotItemMapper = hotItemMapper;
        this.signalAdapterRegistry = signalAdapterRegistry;
    }

    /**
     * Promotes the highest-priority active member of the cluster to primary,
     * demoting any other active primary in the same cluster.
     *
     * <p>No-op when the cluster has no active members or already has the
     * best member marked as primary.
     */
    public void reselect(HotClusterEntity cluster) {
        if (cluster == null || cluster.getId() == null) {
            return;
        }
        List<HotClusterItemEntity> members = clusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, cluster.getId())
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
        if (members.isEmpty()) {
            return;
        }

        HotClusterItemEntity best = pickBest(members);
        if (best == null) {
            return;
        }
        Long currentPrimaryId = cluster.getPrimaryItemId();
        if (currentPrimaryId != null && currentPrimaryId.equals(best.getHotItemId())
                && Boolean.TRUE.equals(best.getIsPrimary())) {
            return;
        }

        // Clear all primary flags first to respect the unique partial index.
        clusterItemMapper.update(null,
                new LambdaUpdateWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, cluster.getId())
                        .isNull(HotClusterItemEntity::getRemovedAt)
                        .eq(HotClusterItemEntity::getIsPrimary, true)
                        .set(HotClusterItemEntity::getIsPrimary, false)
        );

        best.setIsPrimary(true);
        clusterItemMapper.updateById(best);

        cluster.setPrimaryItemId(best.getHotItemId());
        clusterMapper.updateById(cluster);
        log.debug("Re-selected primary for cluster {}: item {}", cluster.getId(), best.getHotItemId());
    }

    private HotClusterItemEntity pickBest(List<HotClusterItemEntity> members) {
        return members.stream()
                .min(Comparator
                        .comparingInt(this::roleRank).reversed()
                        .thenComparingLong(m -> m.getHotItemId() == null ? Long.MAX_VALUE : m.getHotItemId()))
                .orElse(null);
    }

    private int roleRank(HotClusterItemEntity membership) {
        HotItemEntity hotItem = hotItemMapper.selectById(membership.getHotItemId());
        if (hotItem == null) {
            return roleRankOf(null);
        }
        SourceRole role = adaptSafe(hotItem);
        return roleRankOf(role);
    }

    private SourceRole adaptSafe(HotItemEntity hotItem) {
        if (!signalAdapterRegistry.hasAdapter(hotItem.getSourceType())) {
            return null;
        }
        try {
            NormalizedSignal signal = signalAdapterRegistry.adapt(hotItem);
            return signal.sourceRole();
        } catch (RuntimeException ex) {
            // Adapter failures should never block primary selection.
            return null;
        }
    }

    private int roleRankOf(SourceRole role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case PRIMARY -> 5;
            case ADOPTION -> 4;
            case MEDIA -> 3;
            case COMMUNITY -> 2;
            case DISCOVERY -> 1;
        };
    }
}
