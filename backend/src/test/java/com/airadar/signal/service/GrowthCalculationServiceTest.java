package com.airadar.signal.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.signal.entity.SignalSnapshotEntity;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.GrowthMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrowthCalculationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldCalculatePositiveGrowthWithHighConfidence() {
        SignalSnapshotService signalSnapshotService = mock(SignalSnapshotService.class);
        GrowthCalculationService service = new GrowthCalculationService(signalSnapshotService);
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
        GrowthCalculationService service = new GrowthCalculationService(signalSnapshotService);
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
        GrowthCalculationService service = new GrowthCalculationService(signalSnapshotService);
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
        GrowthCalculationService service = new GrowthCalculationService(signalSnapshotService);
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
        GrowthCalculationService service = new GrowthCalculationService(signalSnapshotService);

        assertThatThrownBy(() -> service.calculate(1L, "6h"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Only window=24h is supported in Phase 14.");
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
