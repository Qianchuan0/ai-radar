package com.airadar.signal.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.signal.adapter.SourceSignalAdapter;
import com.airadar.signal.adapter.SourceSignalAdapterRegistry;
import com.airadar.signal.entity.SignalSnapshotEntity;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.GrowthMetrics;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.RawMetricDelta;
import com.airadar.signal.model.TrendMetrics;
import com.airadar.signal.model.TrendWindow;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrowthCalculationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldCalculatePositiveGrowthWithHighConfidence() {
        SignalSnapshotService signalSnapshotService = mock(SignalSnapshotService.class);
        GrowthCalculationService service = newService(signalSnapshotService);
        long hotItemId = 101L;
        Instant currentObservedAt = Instant.parse("2026-07-17T00:00:00Z");
        SignalSnapshotEntity current = snapshot(1L, hotItemId, currentObservedAt, signal(80, 70, 60, 0, null));
        SignalSnapshotEntity historical = snapshot(2L, hotItemId, currentObservedAt.minusSeconds(24 * 3600), signal(30, 20, 10, 0, null));

        when(signalSnapshotService.latestSnapshot(hotItemId)).thenReturn(current);
        when(signalSnapshotService.listWithinWindow(
            hotItemId,
            currentObservedAt.minusSeconds((24 + 3) * 3600L),
            currentObservedAt.minusSeconds((24 - 3) * 3600L)
        )).thenReturn(List.of(historical));

        GrowthMetrics metrics = service.calculate(hotItemId, "24h");

        assertThat(metrics.attentionDelta()).isEqualTo(50.0);
        assertThat(metrics.discussionDelta()).isEqualTo(50.0);
        assertThat(metrics.adoptionDelta()).isEqualTo(50.0);
        assertThat(metrics.relevanceDelta()).isZero();
        assertThat(metrics.confidence()).isEqualTo(GrowthConfidence.HIGH);
        assertThat(metrics.momentumScore()).isEqualTo(37.5);
    }

    @Test
    void shouldCalculateRankImprovementForSearchSources() {
        SignalSnapshotService signalSnapshotService = mock(SignalSnapshotService.class);
        GrowthCalculationService service = newService(signalSnapshotService);
        long hotItemId = 202L;
        Instant currentObservedAt = Instant.parse("2026-07-17T00:00:00Z");
        SignalSnapshotEntity current = snapshot(1L, hotItemId, currentObservedAt, signal(0, 0, 0, 80, 5));
        SignalSnapshotEntity historical = snapshot(2L, hotItemId, currentObservedAt.minusSeconds(24 * 3600), signal(0, 0, 0, 20, 20));

        when(signalSnapshotService.latestSnapshot(hotItemId)).thenReturn(current);
        when(signalSnapshotService.listWithinWindow(
            hotItemId,
            currentObservedAt.minusSeconds((24 + 3) * 3600L),
            currentObservedAt.minusSeconds((24 - 3) * 3600L)
        )).thenReturn(List.of(historical));

        GrowthMetrics metrics = service.calculate(hotItemId, "24h");

        assertThat(metrics.relevanceDelta()).isEqualTo(60.0);
        assertThat(metrics.rankDelta()).isEqualTo(15);
        assertThat(metrics.momentumScore()).isEqualTo(30.0);
        assertThat(metrics.confidence()).isEqualTo(GrowthConfidence.HIGH);
    }

    @Test
    void shouldReturnUnknownWhenHistoricalSnapshotIsMissing() {
        SignalSnapshotService signalSnapshotService = mock(SignalSnapshotService.class);
        GrowthCalculationService service = newService(signalSnapshotService);
        long hotItemId = 303L;
        Instant currentObservedAt = Instant.parse("2026-07-17T00:00:00Z");
        SignalSnapshotEntity current = snapshot(1L, hotItemId, currentObservedAt, signal(10, 10, 10, 0, null));

        when(signalSnapshotService.latestSnapshot(hotItemId)).thenReturn(current);
        when(signalSnapshotService.listWithinWindow(
            hotItemId,
            currentObservedAt.minusSeconds((24 + 3) * 3600L),
            currentObservedAt.minusSeconds((24 - 3) * 3600L)
        )).thenReturn(List.of());

        GrowthMetrics metrics = service.calculate(hotItemId, "24h");

        assertThat(metrics.confidence()).isEqualTo(GrowthConfidence.UNKNOWN);
        assertThat(metrics.attentionDelta()).isNull();
        assertThat(metrics.rankDelta()).isNull();
    }

    @Test
    void shouldMarkMetricResetWhenCoreSignalDrops() {
        SignalSnapshotService signalSnapshotService = mock(SignalSnapshotService.class);
        GrowthCalculationService service = newService(signalSnapshotService);
        long hotItemId = 404L;
        Instant currentObservedAt = Instant.parse("2026-07-17T00:00:00Z");
        SignalSnapshotEntity current = snapshot(1L, hotItemId, currentObservedAt, signal(40, 10, 5, 0, null));
        SignalSnapshotEntity historical = snapshot(2L, hotItemId, currentObservedAt.minusSeconds(24 * 3600), signal(60, 10, 5, 0, null));

        when(signalSnapshotService.latestSnapshot(hotItemId)).thenReturn(current);
        when(signalSnapshotService.listWithinWindow(
            hotItemId,
            currentObservedAt.minusSeconds((24 + 3) * 3600L),
            currentObservedAt.minusSeconds((24 - 3) * 3600L)
        )).thenReturn(List.of(historical));

        GrowthMetrics metrics = service.calculate(hotItemId, "24h");

        assertThat(metrics.attentionDelta()).isEqualTo(-20.0);
        assertThat(metrics.confidence()).isEqualTo(GrowthConfidence.METRIC_RESET);
        assertThat(metrics.momentumScore()).isZero();
    }

    @Test
    void shouldRejectUnsupportedWindow() {
        SignalSnapshotService signalSnapshotService = mock(SignalSnapshotService.class);
        GrowthCalculationService service = newService(signalSnapshotService);

        assertThatThrownBy(() -> service.calculate(1L, "6h"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Only window=24h is supported in Phase 14.");
    }

    private static GrowthCalculationService newService(SignalSnapshotService signalSnapshotService) {
        SourceSignalAdapterRegistry registry = mock(SourceSignalAdapterRegistry.class);
        when(registry.hasAdapter(any())).thenReturn(false);
        return new GrowthCalculationService(signalSnapshotService, registry);
    }

    // ------------------------------------------------------------------
    // Phase 18A: calculateTrend (multi-window, source-aware deltas)
    // ------------------------------------------------------------------

    @Test
    void calculateTrendShouldProduceSourceAwareRawDeltasForGitHub() {
        SignalSnapshotService snapshots = mock(SignalSnapshotService.class);
        SourceSignalAdapterRegistry registry = githubRegistry();
        GrowthCalculationService service = new GrowthCalculationService(snapshots, registry);

        long hotItemId = 1_001L;
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        SignalSnapshotEntity current = githubSnapshot(
            10L, hotItemId, now,
            rawGithub(4200, 310, 80, 21),
            signal(80, 50, 70, 0, null)
        );
        SignalSnapshotEntity historical = githubSnapshot(
            11L, hotItemId, now.minusSeconds(6 * 3600),
            rawGithub(3800, 300, 75, 18),
            signal(60, 40, 55, 0, null)
        );

        when(snapshots.latestSnapshot(hotItemId)).thenReturn(current);
        // First call (current window) returns the historical snapshot; the
        // acceleration lookup hits a different time range and returns empty
        // so acceleration stays null in this test.
        when(snapshots.listWithinWindow(
            eq(hotItemId),
            any(), any()
        )).thenReturn(List.of(historical)).thenReturn(List.of());

        TrendMetrics metrics = service.calculateTrend(hotItemId, TrendWindow.H6);

        assertThat(metrics.window()).isEqualTo("6h");
        assertThat(metrics.attentionDelta()).isEqualTo(20.0);
        assertThat(metrics.confidence()).isEqualTo(GrowthConfidence.HIGH);
        // All three cumulative counters increased — no anomaly flag.
        assertThat(metrics.rawMetricDeltas()).extracting(RawMetricDelta::metric)
            .contains("forksCount", "openIssuesCount", "stargazersCount", "watchersCount");
        RawMetricDelta stars = findDelta(metrics, "stargazersCount");
        assertThat(stars.delta()).isEqualTo(400.0);
        assertThat(stars.growthRate()).isCloseTo(400.0 / 3800.0, within(1e-9));
        assertThat(stars.semantics()).isEqualTo(MetricSemantics.MONOTONIC_CUMULATIVE);
        assertThat(stars.anomaly()).isFalse();
        // openIssues dropped from 18 to 21 — volatile, not anomaly.
        RawMetricDelta issues = findDelta(metrics, "openIssuesCount");
        assertThat(issues.delta()).isEqualTo(3.0);
        assertThat(issues.anomaly()).isFalse();
        // growth rate aggregates monotonic + volatile contributions.
        assertThat(metrics.growthRate()).isNotNull().isPositive();
        // Acceleration requires a third snapshot; none is stubbed here.
        assertThat(metrics.acceleration()).isNull();
    }

    @Test
    void calculateTrendShouldFlagMonotonicCumulativeDropsAsAnomalyAndReset() {
        SignalSnapshotService snapshots = mock(SignalSnapshotService.class);
        SourceSignalAdapterRegistry registry = githubRegistry();
        GrowthCalculationService service = new GrowthCalculationService(snapshots, registry);

        long hotItemId = 2_002L;
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        SignalSnapshotEntity current = githubSnapshot(
            20L, hotItemId, now,
            rawGithub(3_700, 300, 75, 21), // stars dropped 3800 -> 3700
            signal(70, 40, 60, 0, null)
        );
        SignalSnapshotEntity historical = githubSnapshot(
            21L, hotItemId, now.minusSeconds(6 * 3600),
            rawGithub(3_800, 300, 75, 18),
            signal(60, 40, 55, 0, null)
        );

        when(snapshots.latestSnapshot(hotItemId)).thenReturn(current);
        when(snapshots.listWithinWindow(eq(hotItemId), any(), any())).thenReturn(List.of(historical));

        TrendMetrics metrics = service.calculateTrend(hotItemId, TrendWindow.H6);

        RawMetricDelta stars = findDelta(metrics, "stargazersCount");
        assertThat(stars.delta()).isEqualTo(-100.0);
        assertThat(stars.anomaly()).isTrue();
        assertThat(metrics.confidence()).isEqualTo(GrowthConfidence.METRIC_RESET);
    }

    @Test
    void calculateTrendShouldNotFlagRankMovementAsResetForSearchSources() {
        SignalSnapshotService snapshots = mock(SignalSnapshotService.class);
        SourceSignalAdapterRegistry registry = bingRegistry();
        GrowthCalculationService service = new GrowthCalculationService(snapshots, registry);

        long hotItemId = 3_003L;
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        // rank drifts 1 -> 5 (relevance drops); must NOT trigger METRIC_RESET.
        SignalSnapshotEntity current = searchSnapshot(
            30L, hotItemId, now,
            rawSearch(5, 10),
            signal(0, 0, 0, 50, 5)
        );
        SignalSnapshotEntity historical = searchSnapshot(
            31L, hotItemId, now.minusSeconds(6 * 3600),
            rawSearch(1, 10),
            signal(0, 0, 0, 100, 1)
        );

        when(snapshots.latestSnapshot(hotItemId)).thenReturn(current);
        when(snapshots.listWithinWindow(eq(hotItemId), any(), any())).thenReturn(List.of(historical));

        TrendMetrics metrics = service.calculateTrend(hotItemId, TrendWindow.H6);

        assertThat(metrics.confidence()).isNotEqualTo(GrowthConfidence.METRIC_RESET);
        RawMetricDelta rank = findDelta(metrics, "rank");
        assertThat(rank.semantics()).isEqualTo(MetricSemantics.RANK_LIKE_REVERSIBLE);
        assertThat(rank.delta()).isEqualTo(4.0);
        assertThat(rank.anomaly()).isFalse();
    }

    @Test
    void calculateTrendShouldComputeAccelerationWhenPreviousWindowSnapshotExists() {
        SignalSnapshotService snapshots = mock(SignalSnapshotService.class);
        SourceSignalAdapterRegistry registry = githubRegistry();
        GrowthCalculationService service = new GrowthCalculationService(snapshots, registry);

        long hotItemId = 4_004L;
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        Instant sixHoursAgo = now.minusSeconds(6 * 3600);
        Instant twelveHoursAgo = now.minusSeconds(12 * 3600);

        SignalSnapshotEntity current = githubSnapshot(
            40L, hotItemId, now,
            rawGithub(5_000, 400, 100, 30),
            signal(90, 60, 80, 0, null)
        );
        SignalSnapshotEntity mid = githubSnapshot(
            41L, hotItemId, sixHoursAgo,
            rawGithub(4_500, 380, 90, 25),
            signal(70, 50, 70, 0, null)
        );
        SignalSnapshotEntity previous = githubSnapshot(
            42L, hotItemId, twelveHoursAgo,
            rawGithub(4_400, 370, 88, 22),
            signal(65, 45, 65, 0, null)
        );

        when(snapshots.latestSnapshot(hotItemId)).thenReturn(current);
        // First call: current window candidates (returns mid).
        // Second call: previous window candidates (returns previous).
        when(snapshots.listWithinWindow(eq(hotItemId), any(), any()))
            .thenReturn(List.of(mid))
            .thenReturn(List.of(previous));

        TrendMetrics metrics = service.calculateTrend(hotItemId, TrendWindow.H6);

        assertThat(metrics.acceleration()).isNotNull();
        // Both windows show positive momentum; current window should be larger.
        assertThat(metrics.acceleration()).isPositive();
    }

    @Test
    void calculateTrendShouldReturnUnknownWhenHistoricalSnapshotIsMissing() {
        SignalSnapshotService snapshots = mock(SignalSnapshotService.class);
        SourceSignalAdapterRegistry registry = githubRegistry();
        GrowthCalculationService service = new GrowthCalculationService(snapshots, registry);

        long hotItemId = 5_005L;
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        SignalSnapshotEntity current = githubSnapshot(
            50L, hotItemId, now,
            rawGithub(1_000, 100, 50, 10),
            signal(10, 5, 5, 0, null)
        );

        when(snapshots.latestSnapshot(hotItemId)).thenReturn(current);
        when(snapshots.listWithinWindow(eq(hotItemId), any(), any())).thenReturn(List.of());

        TrendMetrics metrics = service.calculateTrend(hotItemId, TrendWindow.H1);

        assertThat(metrics.confidence()).isEqualTo(GrowthConfidence.UNKNOWN);
        assertThat(metrics.rawMetricDeltas()).isEmpty();
        assertThat(metrics.growthRate()).isNull();
        assertThat(metrics.acceleration()).isNull();
    }

    @Test
    void trendWindowShouldParseCanonicalAndLegacyCodes() {
        assertThat(TrendWindow.parse("1h")).isEqualTo(TrendWindow.H1);
        assertThat(TrendWindow.parse("6h")).isEqualTo(TrendWindow.H6);
        assertThat(TrendWindow.parse("24h")).isEqualTo(TrendWindow.H24);
        assertThat(TrendWindow.parse("3d")).isEqualTo(TrendWindow.D3);
        assertThat(TrendWindow.H24.code()).isEqualTo("24h");
        assertThat(TrendWindow.D3.code()).isEqualTo("3d");
        assertThatThrownBy(() -> TrendWindow.parse("7d"))
            .isInstanceOf(BusinessException.class);
    }

    private static RawMetricDelta findDelta(TrendMetrics metrics, String name) {
        return metrics.rawMetricDeltas().stream()
            .filter(delta -> name.equals(delta.metric()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing delta for " + name));
    }

    private static SourceSignalAdapterRegistry githubRegistry() {
        SourceSignalAdapter adapter = mock(SourceSignalAdapter.class);
        when(adapter.metricSemantics()).thenReturn(Map.of(
            "stargazersCount", MetricSemantics.MONOTONIC_CUMULATIVE,
            "forksCount", MetricSemantics.MONOTONIC_CUMULATIVE,
            "watchersCount", MetricSemantics.MONOTONIC_CUMULATIVE,
            "openIssuesCount", MetricSemantics.VOLATILE_SOCIAL
        ));
        SourceSignalAdapterRegistry registry = mock(SourceSignalAdapterRegistry.class);
        when(registry.hasAdapter(any())).thenReturn(true);
        when(registry.getRequired(any())).thenReturn(adapter);
        return registry;
    }

    private static SourceSignalAdapterRegistry bingRegistry() {
        SourceSignalAdapter adapter = mock(SourceSignalAdapter.class);
        when(adapter.metricSemantics()).thenReturn(Map.of(
            "rank", MetricSemantics.RANK_LIKE_REVERSIBLE,
            "totalCount", MetricSemantics.RELEVANCE_SCORE
        ));
        SourceSignalAdapterRegistry registry = mock(SourceSignalAdapterRegistry.class);
        when(registry.hasAdapter(any())).thenReturn(true);
        when(registry.getRequired(any())).thenReturn(adapter);
        return registry;
    }

    private static SignalSnapshotEntity githubSnapshot(
        Long id, Long hotItemId, Instant observedAt, ObjectNode rawMetrics, ObjectNode normalized
    ) {
        SignalSnapshotEntity entity = new SignalSnapshotEntity();
        entity.setId(id);
        entity.setHotItemId(hotItemId);
        entity.setObservedAt(observedAt);
        entity.setSourceType(SourceType.GITHUB);
        entity.setRawMetrics(rawMetrics);
        entity.setNormalizedSignal(normalized);
        return entity;
    }

    private static SignalSnapshotEntity searchSnapshot(
        Long id, Long hotItemId, Instant observedAt, ObjectNode rawMetrics, ObjectNode normalized
    ) {
        SignalSnapshotEntity entity = new SignalSnapshotEntity();
        entity.setId(id);
        entity.setHotItemId(hotItemId);
        entity.setObservedAt(observedAt);
        entity.setSourceType(SourceType.BING_SEARCH);
        entity.setRawMetrics(rawMetrics);
        entity.setNormalizedSignal(normalized);
        return entity;
    }

    private static ObjectNode rawGithub(int stars, int forks, int watchers, int openIssues) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("stargazersCount", stars);
        node.put("forksCount", forks);
        node.put("watchersCount", watchers);
        node.put("openIssuesCount", openIssues);
        return node;
    }

    private static ObjectNode rawSearch(int rank, int totalCount) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("rank", rank);
        node.put("totalCount", totalCount);
        return node;
    }

    private static SignalSnapshotEntity snapshot(Long id, Long hotItemId, Instant observedAt, ObjectNode signal) {
        SignalSnapshotEntity entity = new SignalSnapshotEntity();
        entity.setId(id);
        entity.setHotItemId(hotItemId);
        entity.setObservedAt(observedAt);
        entity.setNormalizedSignal(signal);
        return entity;
    }

    private static ObjectNode signal(double attention, double discussion, double adoption, double relevance, Integer rank) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("attention", attention);
        node.put("discussion", discussion);
        node.put("adoption", adoption);
        node.put("relevance", relevance);
        if (rank != null) {
            node.put("rank", rank);
        }
        return node;
    }
}
