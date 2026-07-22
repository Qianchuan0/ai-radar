package com.airadar.cluster.service;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.model.ClusterTrend;
import com.airadar.cluster.model.ClusterTrendState;
import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.RawMetricDelta;
import com.airadar.signal.model.TrendMetrics;
import com.airadar.signal.model.TrendWindow;
import com.airadar.signal.service.GrowthCalculationService;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterTrendServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldAggregateMultipleContributingItemsIntoRisingState() {
        long clusterId = 100L;
        HotClusterEntity cluster = cluster(clusterId);
        HotItemEntity primary = hotItem(1L, SourceType.GITHUB, "https://github.com/org/repo");
        HotItemEntity secondary = hotItem(2L, SourceType.HACKER_NEWS, "https://news.ycombinator.com/item?id=1");

        HotClusterItemMapper itemMapper = mapperReturning(clusterId, primary, secondary);
        HotItemMapper hotItemMapper = hotItemMapperReturning(primary, secondary);
        GrowthCalculationService growth = growthServiceReturning(
            primary.getId(), trend(80.0, 5.0, GrowthConfidence.HIGH),
            secondary.getId(), trend(60.0, 3.0, GrowthConfidence.MEDIUM)
        );

        ClusterTrendService service = new ClusterTrendService(
            clusterMapperReturning(cluster),
            itemMapper,
            hotItemMapper,
            growth,
            new UrlCanonicalizer()
        );

        ClusterTrend trend = service.aggregate(clusterId, TrendWindow.H6);

        assertThat(trend.hotClusterId()).isEqualTo(clusterId);
        assertThat(trend.trendState()).isEqualTo(ClusterTrendState.RISING);
        // Both items contributed positive deltas; aggregated momentum should be
        // the average of their per-item scores and stay positive.
        assertThat(trend.momentumScore()).isPositive();
        assertThat(trend.confidence()).isEqualTo(GrowthConfidence.MEDIUM);
        assertThat(trend.contributingItems()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(trend.skippedItems()).isEmpty();
    }

    @Test
    void shouldDeduplicateDiscoverySourcesByCanonicalUrl() {
        long clusterId = 200L;
        HotClusterEntity cluster = cluster(clusterId);
        // Three search sources point to the same canonical URL (tracking params
        // are stripped by UrlCanonicalizer).
        HotItemEntity bing = hotItem(10L, SourceType.BING_SEARCH, "https://example.com/post?utm_source=bing");
        HotItemEntity duck = hotItem(11L, SourceType.DUCKDUCKGO_SEARCH, "https://example.com/post?utm_medium=duck");
        HotItemEntity sogou = hotItem(12L, SourceType.SOGOU_SEARCH, "https://example.com/post?utm_campaign=sogou");

        HotClusterItemMapper itemMapper = mapperReturning(clusterId, bing, duck, sogou);
        HotItemMapper hotItemMapper = hotItemMapperReturning(bing, duck, sogou);

        // Each search source would contribute 30 momentum on its own; dedup
        // keeps only one winner so total momentum reflects a single discovery.
        GrowthCalculationService growth = growthServiceReturning(
            bing.getId(), trend(30.0, 1.0, GrowthConfidence.HIGH),
            duck.getId(), trend(30.0, 1.0, GrowthConfidence.HIGH),
            sogou.getId(), trend(30.0, 1.0, GrowthConfidence.HIGH)
        );

        ClusterTrendService service = new ClusterTrendService(
            clusterMapperReturning(cluster),
            itemMapper,
            hotItemMapper,
            growth,
            new UrlCanonicalizer()
        );

        ClusterTrend trend = service.aggregate(clusterId, TrendWindow.H24);

        assertThat(trend.contributingItems()).hasSize(1);
        assertThat(trend.contributingItems()).containsExactly(10L);
        // The other two discovery items are simply dropped (not added to skipped),
        // because dedup happens before confidence classification.
        assertThat(trend.contributingItems()).doesNotContain(11L, 12L);
    }

    @Test
    void shouldClassifyAsPeakingWhenSignedDeltaPositiveButAccelerationNegative() {
        long clusterId = 300L;
        HotClusterEntity cluster = cluster(clusterId);
        HotItemEntity item = hotItem(20L, SourceType.GITHUB, "https://github.com/x/y");

        HotClusterItemMapper itemMapper = mapperReturning(clusterId, item);
        HotItemMapper hotItemMapper = hotItemMapperReturning(item);
        // Positive normalized deltas (deltaSum = 35) but negative acceleration.
        GrowthCalculationService growth = growthServiceReturning(
            item.getId(), trend(50.0, 10.0, 5.0, 20.0, -3.0, GrowthConfidence.HIGH)
        );

        ClusterTrendService service = new ClusterTrendService(
            clusterMapperReturning(cluster),
            itemMapper,
            hotItemMapper,
            growth,
            new UrlCanonicalizer()
        );

        ClusterTrend trend = service.aggregate(clusterId, TrendWindow.H6);

        assertThat(trend.trendState()).isEqualTo(ClusterTrendState.PEAKING);
    }

    @Test
    void shouldClassifyAsCoolingWhenSignedDeltaNegative() {
        long clusterId = 400L;
        HotClusterEntity cluster = cluster(clusterId);
        HotItemEntity item = hotItem(30L, SourceType.HACKER_NEWS, "https://news.ycombinator.com/item?id=9");

        HotClusterItemMapper itemMapper = mapperReturning(clusterId, item);
        HotItemMapper hotItemMapper = hotItemMapperReturning(item);
        // Negative normalized deltas: interest is cooling.
        GrowthCalculationService growth = growthServiceReturning(
            item.getId(), trend(5.0, -15.0, -10.0, -5.0, -1.0, GrowthConfidence.MEDIUM)
        );

        ClusterTrendService service = new ClusterTrendService(
            clusterMapperReturning(cluster),
            itemMapper,
            hotItemMapper,
            growth,
            new UrlCanonicalizer()
        );

        ClusterTrend trend = service.aggregate(clusterId, TrendWindow.H24);

        assertThat(trend.trendState()).isEqualTo(ClusterTrendState.COOLING);
    }

    @Test
    void shouldSkipItemsWithUnknownConfidenceAndFallBackToNewWhenAllSkipped() {
        long clusterId = 500L;
        HotClusterEntity cluster = cluster(clusterId);
        HotItemEntity fresh = hotItem(40L, SourceType.GITHUB, "https://github.com/x/y");

        HotClusterItemMapper itemMapper = mapperReturning(clusterId, fresh);
        HotItemMapper hotItemMapper = hotItemMapperReturning(fresh);
        GrowthCalculationService growth = growthServiceReturning(
            fresh.getId(), unknownTrend()
        );

        ClusterTrendService service = new ClusterTrendService(
            clusterMapperReturning(cluster),
            itemMapper,
            hotItemMapper,
            growth,
            new UrlCanonicalizer()
        );

        ClusterTrend trend = service.aggregate(clusterId, TrendWindow.H1);

        assertThat(trend.trendState()).isEqualTo(ClusterTrendState.NEW);
        assertThat(trend.contributingItems()).isEmpty();
        assertThat(trend.skippedItems()).containsExactly(40L);
        assertThat(trend.confidence()).isEqualTo(GrowthConfidence.UNKNOWN);
    }

    @Test
    void shouldAggregateRawDeltasByMetricKeyAcrossItems() {
        long clusterId = 600L;
        HotClusterEntity cluster = cluster(clusterId);
        HotItemEntity left = hotItem(50L, SourceType.GITHUB, "https://github.com/a/b");
        HotItemEntity right = hotItem(51L, SourceType.GITHUB, "https://github.com/c/d");

        HotClusterItemMapper itemMapper = mapperReturning(clusterId, left, right);
        HotItemMapper hotItemMapper = hotItemMapperReturning(left, right);

        TrendMetrics leftMetrics = new TrendMetrics(
            50L, "6h", 10.0, 5.0, 20.0, 0.0, null,
            40.0,
            GrowthConfidence.HIGH,
            List.of(new RawMetricDelta("stargazersCount", 100.0, 150.0, 50.0, 0.5, MetricSemantics.MONOTONIC_CUMULATIVE, false)),
            0.5,
            10.0,
            null,
            Instant.parse("2026-07-17T12:00:00Z"),
            Instant.parse("2026-07-17T06:00:00Z"),
            Instant.parse("2026-07-17T12:00:01Z")
        );
        TrendMetrics rightMetrics = new TrendMetrics(
            51L, "6h", 5.0, 0.0, 10.0, 0.0, null,
            20.0,
            GrowthConfidence.MEDIUM,
            List.of(new RawMetricDelta("stargazersCount", 200.0, 250.0, 50.0, 0.25, MetricSemantics.MONOTONIC_CUMULATIVE, false)),
            0.25,
            5.0,
            null,
            Instant.parse("2026-07-17T12:00:00Z"),
            Instant.parse("2026-07-17T06:00:00Z"),
            Instant.parse("2026-07-17T12:00:01Z")
        );
        GrowthCalculationService growth = mock(GrowthCalculationService.class);
        when(growth.calculateTrend(eq(50L), any())).thenReturn(leftMetrics);
        when(growth.calculateTrend(eq(51L), any())).thenReturn(rightMetrics);

        ClusterTrendService service = new ClusterTrendService(
            clusterMapperReturning(cluster),
            itemMapper,
            hotItemMapper,
            growth,
            new UrlCanonicalizer()
        );

        ClusterTrend trend = service.aggregate(clusterId, TrendWindow.H6);

        RawMetricDelta stars = trend.rawMetricDeltas().stream()
            .filter(delta -> "stargazersCount".equals(delta.metric()))
            .findFirst()
            .orElseThrow();
        // 50 + 50 = 100 summed across both GitHub items.
        assertThat(stars.delta()).isEqualTo(100.0);
        // growthRate is averaged: (0.5 + 0.25) / 2.
        assertThat(stars.growthRate()).isCloseTo(0.375, within(1e-9));
        assertThat(trend.normalizedDeltas()).containsEntry("adoption", 30.0);
    }

    @Test
    void shouldReturnUnknownWhenClusterHasNoActiveItems() {
        long clusterId = 700L;
        HotClusterEntity cluster = cluster(clusterId);

        HotClusterItemMapper itemMapper = mock(HotClusterItemMapper.class);
        when(itemMapper.selectList(any())).thenReturn(List.of());

        ClusterTrendService service = new ClusterTrendService(
            clusterMapperReturning(cluster),
            itemMapper,
            mock(HotItemMapper.class),
            mock(GrowthCalculationService.class),
            new UrlCanonicalizer()
        );

        ClusterTrend trend = service.aggregate(clusterId, TrendWindow.H24);

        assertThat(trend.trendState()).isEqualTo(ClusterTrendState.UNKNOWN);
        assertThat(trend.contributingItems()).isEmpty();
        assertThat(trend.momentumScore()).isNull();
    }

    private static HotClusterEntity cluster(long id) {
        HotClusterEntity entity = new HotClusterEntity();
        entity.setId(id);
        return entity;
    }

    private static HotItemEntity hotItem(long id, SourceType sourceType, String sourceUrl) {
        HotItemEntity entity = new HotItemEntity();
        entity.setId(id);
        entity.setSourceType(sourceType);
        entity.setSourceUrl(sourceUrl);
        ObjectNode metrics = OBJECT_MAPPER.createObjectNode();
        // Bing rank 1 = winner when dedup runs.
        metrics.put("rank", 1);
        entity.setMetrics(metrics);
        return entity;
    }

    private static HotClusterItemMapper mapperReturning(long clusterId, HotItemEntity... items) {
        HotClusterItemMapper mapper = mock(HotClusterItemMapper.class);
        List<HotClusterItemEntity> memberships = new java.util.ArrayList<>();
        for (HotItemEntity item : items) {
            HotClusterItemEntity membership = new HotClusterItemEntity();
            membership.setHotClusterId(clusterId);
            membership.setHotItemId(item.getId());
            memberships.add(membership);
        }
        when(mapper.selectList(any())).thenReturn(memberships);
        return mapper;
    }

    private static HotItemMapper hotItemMapperReturning(HotItemEntity... items) {
        HotItemMapper mapper = mock(HotItemMapper.class);
        when(mapper.selectBatchIds(any())).thenReturn(List.of(items));
        return mapper;
    }

    private static HotClusterMapper clusterMapperReturning(HotClusterEntity cluster) {
        HotClusterMapper mapper = mock(HotClusterMapper.class);
        when(mapper.selectById(cluster.getId())).thenReturn(cluster);
        return mapper;
    }

    private static GrowthCalculationService growthServiceReturning(
        Object... idThenMetrics
    ) {
        GrowthCalculationService service = mock(GrowthCalculationService.class);
        for (int i = 0; i < idThenMetrics.length; i += 2) {
            long itemId = (Long) idThenMetrics[i];
            TrendMetrics metrics = (TrendMetrics) idThenMetrics[i + 1];
            when(service.calculateTrend(eq(itemId), any())).thenReturn(metrics);
        }
        return service;
    }

    private static TrendMetrics trend(double momentum, double acceleration, GrowthConfidence confidence) {
        // The 3-arg overload preserves the original (positive) deltas so that
        // momentum in the 0..100 range roughly matches; the real classification
        // signal comes from acceleration + confidence.
        return trend(10.0, 5.0, 20.0, 0.0, acceleration, confidence);
    }

    private static TrendMetrics trend(
        double attentionDelta,
        double discussionDelta,
        double adoptionDelta,
        double relevanceDelta,
        double acceleration,
        GrowthConfidence confidence
    ) {
        double momentum = Math.max(0.0, attentionDelta) * 0.25
            + Math.max(0.0, discussionDelta) * 0.25
            + Math.max(0.0, adoptionDelta) * 0.25
            + Math.max(0.0, relevanceDelta) * 0.25;
        return new TrendMetrics(
            null, "6h",
            attentionDelta, discussionDelta, adoptionDelta, relevanceDelta, null,
            Math.max(0.0, Math.min(100.0, momentum)),
            confidence,
            List.of(),
            0.1,
            10.0,
            acceleration,
            Instant.parse("2026-07-17T12:00:00Z"),
            Instant.parse("2026-07-17T06:00:00Z"),
            Instant.parse("2026-07-17T12:00:01Z")
        );
    }

    private static TrendMetrics unknownTrend() {
        return new TrendMetrics(
            null, "1h",
            null, null, null, null, null,
            null,
            GrowthConfidence.UNKNOWN,
            List.of(),
            null,
            null,
            null,
            null, null,
            Instant.parse("2026-07-17T12:00:01Z")
        );
    }
}
