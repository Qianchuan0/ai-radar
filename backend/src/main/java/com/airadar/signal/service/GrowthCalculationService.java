package com.airadar.signal.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.signal.entity.SignalSnapshotEntity;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.GrowthMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class GrowthCalculationService {

    static final String WINDOW_24H = "24h";
    static final Duration TARGET_WINDOW = Duration.ofHours(24);
    static final Duration MAX_DEVIATION = Duration.ofHours(3);
    static final Duration HIGH_CONFIDENCE_DEVIATION = Duration.ofMinutes(30);
    static final Duration MEDIUM_CONFIDENCE_DEVIATION = Duration.ofMinutes(90);

    private final SignalSnapshotService signalSnapshotService;

    public GrowthCalculationService(SignalSnapshotService signalSnapshotService) {
        this.signalSnapshotService = signalSnapshotService;
    }

    @Transactional(readOnly = true)
    public GrowthMetrics calculate(long hotItemId, String window) {
        if (!WINDOW_24H.equals(window)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Only window=24h is supported in Phase 14.");
        }

        SignalSnapshotEntity current = signalSnapshotService.latestSnapshot(hotItemId);
        if (current == null) {
            return unknown(hotItemId, window);
        }

        Instant targetObservedAt = current.getObservedAt().minus(TARGET_WINDOW);
        List<SignalSnapshotEntity> candidates = signalSnapshotService.listWithinWindow(
            hotItemId,
            targetObservedAt.minus(MAX_DEVIATION),
            targetObservedAt.plus(MAX_DEVIATION)
        );
        SignalSnapshotEntity historical = nearest(targetObservedAt, candidates);
        if (historical == null) {
            return unknown(hotItemId, window);
        }
        if (current.getObservedAt().isBefore(historical.getObservedAt())) {
            return anomaly(hotItemId, window);
        }

        JsonNode currentSignal = current.getNormalizedSignal();
        JsonNode previousSignal = historical.getNormalizedSignal();
        double attentionDelta = metric(currentSignal, "attention") - metric(previousSignal, "attention");
        double discussionDelta = metric(currentSignal, "discussion") - metric(previousSignal, "discussion");
        double adoptionDelta = metric(currentSignal, "adoption") - metric(previousSignal, "adoption");
        double relevanceDelta = metric(currentSignal, "relevance") - metric(previousSignal, "relevance");
        Integer rankDelta = rankDelta(currentSignal, previousSignal);

        GrowthConfidence confidence = confidenceFor(historical.getObservedAt(), targetObservedAt);
        if (attentionDelta < 0 || discussionDelta < 0 || adoptionDelta < 0 || relevanceDelta < 0) {
            confidence = GrowthConfidence.METRIC_RESET;
        }

        return new GrowthMetrics(
            hotItemId,
            window,
            attentionDelta,
            discussionDelta,
            adoptionDelta,
            relevanceDelta,
            rankDelta,
            momentumScore(attentionDelta, discussionDelta, adoptionDelta, relevanceDelta, rankDelta),
            confidence
        );
    }

    private SignalSnapshotEntity nearest(Instant targetObservedAt, List<SignalSnapshotEntity> candidates) {
        return candidates.stream()
            .min(Comparator.comparing(snapshot -> Duration.between(targetObservedAt, snapshot.getObservedAt()).abs()))
            .orElse(null);
    }

    private GrowthMetrics unknown(long hotItemId, String window) {
        return new GrowthMetrics(hotItemId, window, null, null, null, null, null, null, GrowthConfidence.UNKNOWN);
    }

    private GrowthMetrics anomaly(long hotItemId, String window) {
        return new GrowthMetrics(hotItemId, window, null, null, null, null, null, null, GrowthConfidence.DATA_ANOMALY);
    }

    private GrowthConfidence confidenceFor(Instant observedAt, Instant targetObservedAt) {
        Duration deviation = Duration.between(targetObservedAt, observedAt).abs();
        if (deviation.compareTo(HIGH_CONFIDENCE_DEVIATION) <= 0) {
            return GrowthConfidence.HIGH;
        }
        if (deviation.compareTo(MEDIUM_CONFIDENCE_DEVIATION) <= 0) {
            return GrowthConfidence.MEDIUM;
        }
        return GrowthConfidence.LOW;
    }

    private double metric(JsonNode signal, String field) {
        return signal.path(field).asDouble(0.0);
    }

    private Integer rankDelta(JsonNode currentSignal, JsonNode previousSignal) {
        JsonNode currentRank = currentSignal.get("rank");
        JsonNode previousRank = previousSignal.get("rank");
        if (currentRank == null || currentRank.isNull() || previousRank == null || previousRank.isNull()) {
            return null;
        }
        return previousRank.asInt() - currentRank.asInt();
    }

    private double momentumScore(
        double attentionDelta,
        double discussionDelta,
        double adoptionDelta,
        double relevanceDelta,
        Integer rankDelta
    ) {
        double score = positive(attentionDelta) * 0.25
            + positive(discussionDelta) * 0.25
            + positive(adoptionDelta) * 0.25
            + positive(relevanceDelta) * 0.25;
        if (rankDelta != null && rankDelta > 0) {
            score += Math.min(rankDelta, 20);
        }
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double positive(double value) {
        return Math.max(0.0, value);
    }
}
