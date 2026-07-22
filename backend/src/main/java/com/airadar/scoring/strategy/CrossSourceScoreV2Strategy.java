package com.airadar.scoring.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.model.ClusterTrend;
import com.airadar.cluster.service.ClusterTrendService;
import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.scoring.calculator.ScoreCalculator;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.scoring.strategy.model.ScoreComponents;
import com.airadar.signal.adapter.SourceSignalAdapterRegistry;
import com.airadar.signal.model.GrowthMetrics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.signal.model.TrendWindow;
import com.airadar.signal.service.GrowthCalculationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cross-source V2 scoring strategy.
 *
 * <p>Aggregates seven semantic dimensions (relevance, freshness, momentum,
 * adoption, discussion, authority, evidence diversity) into a single 0..100
 * score. Unlike V1's points/comments baseline, V2 separates cumulative scale
 * from current growth velocity, so fast-rising projects are not buried under
 * established ones.
 *
 * <p><b>Phase 18B refactor:</b> {@code buildContext} now invokes
 * {@link ClusterTrendService#aggregate(long, TrendWindow)} with the 24h window
 * and precomputes source-role groupings + deduped DISCOVERY URLs + earliest
 * credible event time. Calculators no longer read primary-item data for the
 * momentum / adoption / discussion / relevance / freshness dimensions.
 *
 * <p>Runs in its own {@code REQUIRES_NEW} transaction so a V2 failure can never
 * roll back the V1 score written by the {@link ScoringOrchestrator}.
 */
@Component
public class CrossSourceScoreV2Strategy implements ClusterScoringStrategy {

    public static final String VERSION = "cross-source-score-v2";
    private static final Logger log = LoggerFactory.getLogger(CrossSourceScoreV2Strategy.class);

    private final List<ScoreCalculator> calculators;
    private final HotClusterItemMapper clusterItemMapper;
    private final HotItemMapper hotItemMapper;
    private final SourceSignalAdapterRegistry adapterRegistry;
    private final GrowthCalculationService growthCalculationService;
    private final ClusterTrendService clusterTrendService;
    private final UrlCanonicalizer urlCanonicalizer;
    private final HotScoreMapper hotScoreMapper;
    private final ObjectMapper objectMapper;

    public CrossSourceScoreV2Strategy(
            List<ScoreCalculator> calculators,
            HotClusterItemMapper clusterItemMapper,
            HotItemMapper hotItemMapper,
            SourceSignalAdapterRegistry adapterRegistry,
            GrowthCalculationService growthCalculationService,
            ClusterTrendService clusterTrendService,
            UrlCanonicalizer urlCanonicalizer,
            HotScoreMapper hotScoreMapper,
            ObjectMapper objectMapper
    ) {
        this.calculators = calculators.stream()
                .sorted(Comparator.comparing(ScoreCalculator::name))
                .toList();
        this.clusterItemMapper = clusterItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.adapterRegistry = adapterRegistry;
        this.growthCalculationService = growthCalculationService;
        this.clusterTrendService = clusterTrendService;
        this.urlCanonicalizer = urlCanonicalizer;
        this.hotScoreMapper = hotScoreMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HotScoreEntity score(HotClusterEntity cluster) {
        ScoringContext context = buildContext(cluster);

        List<ScoreComponent> components = calculators.stream()
                .map(calculator -> calculator.compute(context))
                .toList();

        double total = components.stream()
                .mapToDouble(ScoreComponent::weightedContribution)
                .sum();
        total = Math.max(0.0, Math.min(100.0, total));

        Instant now = Instant.now();
        ScoreComponents breakdown = new ScoreComponents(VERSION, total, components, now);
        BigDecimal totalScore = BigDecimal.valueOf(total).setScale(4, RoundingMode.HALF_UP);

        HotScoreEntity entity = new HotScoreEntity();
        entity.setHotClusterId(cluster.getId());
        entity.setTotalScore(totalScore);
        entity.setScoreComponents(objectMapper.valueToTree(breakdown));
        entity.setScoringVersion(VERSION);
        entity.setCalculatedAt(now);
        entity.setCreatedAt(now);
        hotScoreMapper.insert(entity);
        return entity;
    }

    private ScoringContext buildContext(HotClusterEntity cluster) {
        List<HotClusterItemEntity> memberships = clusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, cluster.getId())
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );

        List<Long> itemIds = memberships.stream()
                .map(HotClusterItemEntity::getHotItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<HotItemEntity> items = itemIds.isEmpty()
                ? List.of()
                : hotItemMapper.selectBatchIds(itemIds);

        HotItemEntity primaryItem = resolvePrimary(cluster, memberships, items);

        Map<Long, NormalizedSignal> signals = new HashMap<>();
        for (HotItemEntity item : items) {
            try {
                signals.put(item.getId(), adapterRegistry.adapt(item));
            } catch (RuntimeException ex) {
                log.debug("Signal adapter skipped for item {}: {}", item.getId(), ex.toString());
            }
        }

        Map<Long, GrowthMetrics> growthByItem = new HashMap<>();
        for (HotItemEntity item : items) {
            try {
                GrowthMetrics metrics = growthCalculationService.calculate(item.getId(), "24h");
                if (metrics != null) {
                    growthByItem.put(item.getId(), metrics);
                }
            } catch (RuntimeException ex) {
                log.debug("Growth calculation skipped for item {}: {}", item.getId(), ex.toString());
            }
        }

        ClusterTrend clusterTrend = computeClusterTrend(cluster);

        Map<SourceRole, List<HotItemEntity>> itemsByRole = groupItemsByRole(items, signals);
        Map<SourceRole, List<NormalizedSignal>> signalsByRole = groupSignalsByRole(items, signals);
        Set<String> dedupedDiscoveryUrls = collectDedupedDiscoveryUrls(items, signals);
        Instant earliestCredibleEventAt = resolveEarliestCredibleEventAt(cluster, items, signals);

        return new ScoringContext(
                cluster,
                new ArrayList<>(items),
                primaryItem,
                signals,
                growthByItem,
                clusterTrend,
                itemsByRole,
                signalsByRole,
                dedupedDiscoveryUrls,
                earliestCredibleEventAt,
                Instant.now()
        );
    }

    private ClusterTrend computeClusterTrend(HotClusterEntity cluster) {
        try {
            return clusterTrendService.aggregate(cluster.getId(), TrendWindow.H24);
        } catch (RuntimeException ex) {
            log.debug("Cluster trend aggregation skipped for cluster {}: {}",
                    cluster.getId(), ex.toString());
            return null;
        }
    }

    private Map<SourceRole, List<HotItemEntity>> groupItemsByRole(
            List<HotItemEntity> items,
            Map<Long, NormalizedSignal> signals
    ) {
        Map<SourceRole, List<HotItemEntity>> groups = new LinkedHashMap<>();
        for (HotItemEntity item : items) {
            SourceRole role = resolveRole(item, signals);
            if (role == null) {
                continue;
            }
            groups.computeIfAbsent(role, key -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    private Map<SourceRole, List<NormalizedSignal>> groupSignalsByRole(
            List<HotItemEntity> items,
            Map<Long, NormalizedSignal> signals
    ) {
        Map<SourceRole, List<NormalizedSignal>> groups = new LinkedHashMap<>();
        for (HotItemEntity item : items) {
            NormalizedSignal signal = signals.get(item.getId());
            if (signal == null || signal.sourceRole() == null) {
                continue;
            }
            groups.computeIfAbsent(signal.sourceRole(), key -> new ArrayList<>()).add(signal);
        }
        return groups;
    }

    private Set<String> collectDedupedDiscoveryUrls(
            List<HotItemEntity> items,
            Map<Long, NormalizedSignal> signals
    ) {
        Set<String> urls = new HashSet<>();
        for (HotItemEntity item : items) {
            NormalizedSignal signal = signals.get(item.getId());
            if (signal == null || signal.sourceRole() != SourceRole.DISCOVERY) {
                continue;
            }
            String canonical = urlCanonicalizer.canonicalize(item.getSourceUrl());
            if (canonical != null && !canonical.isBlank()) {
                urls.add(canonical);
            }
        }
        return urls;
    }

    private Instant resolveEarliestCredibleEventAt(
            HotClusterEntity cluster,
            List<HotItemEntity> items,
            Map<Long, NormalizedSignal> signals
    ) {
        Instant earliest = null;
        for (HotItemEntity item : items) {
            NormalizedSignal signal = signals.get(item.getId());
            if (signal == null || signal.sourceRole() == SourceRole.DISCOVERY) {
                // Discovery items are not treated as primary evidence of event time.
                continue;
            }
            Instant publishedAt = item.getPublishedAt();
            if (publishedAt == null) {
                continue;
            }
            if (earliest == null || publishedAt.isBefore(earliest)) {
                earliest = publishedAt;
            }
        }
        if (earliest != null) {
            return earliest;
        }
        return cluster.getFirstSeenAt();
    }

    private SourceRole resolveRole(HotItemEntity item, Map<Long, NormalizedSignal> signals) {
        NormalizedSignal signal = signals.get(item.getId());
        if (signal != null && signal.sourceRole() != null) {
            return signal.sourceRole();
        }
        if (item.getSourceType() == null) {
            return null;
        }
        return SourceRole.fromSourceType(item.getSourceType());
    }

    private HotItemEntity resolvePrimary(
            HotClusterEntity cluster,
            List<HotClusterItemEntity> memberships,
            List<HotItemEntity> items
    ) {
        if (items.isEmpty()) {
            return null;
        }
        Long primaryItemId = cluster.getPrimaryItemId();
        if (primaryItemId != null) {
            for (HotItemEntity item : items) {
                if (primaryItemId.equals(item.getId())) {
                    return item;
                }
            }
        }
        for (HotClusterItemEntity membership : memberships) {
            if (Boolean.TRUE.equals(membership.getIsPrimary())) {
                for (HotItemEntity item : items) {
                    if (item.getId().equals(membership.getHotItemId())) {
                        return item;
                    }
                }
            }
        }
        return items.get(0);
    }
}
