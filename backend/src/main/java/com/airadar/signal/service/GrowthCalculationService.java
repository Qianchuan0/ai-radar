package com.airadar.signal.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
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
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class GrowthCalculationService {

    static final String WINDOW_24H = "24h";
    static final Duration TARGET_WINDOW = Duration.ofHours(24);
    static final Duration MAX_DEVIATION = Duration.ofHours(3);
    static final Duration HIGH_CONFIDENCE_DEVIATION = Duration.ofMinutes(30);
    static final Duration MEDIUM_CONFIDENCE_DEVIATION = Duration.ofMinutes(90);

    private final SignalSnapshotService signalSnapshotService;
    private final SourceSignalAdapterRegistry adapterRegistry;

    public GrowthCalculationService(
        SignalSnapshotService signalSnapshotService,
        SourceSignalAdapterRegistry adapterRegistry
    ) {
        this.signalSnapshotService = signalSnapshotService;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * Phase 14 growth endpoint, retained verbatim for backward compatibility.
     *
     * <p>Only {@code window=24h} is accepted. V2 momentum consumers (Phase 15)
     * depend on the exact {@link GrowthMetrics} shape; the Phase 18A multi-window
     * trend layer is exposed through {@link #calculateTrend(long, TrendWindow)}.
     */
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

        GrowthConfidence confidence = confidenceFor(
            historical.getObservedAt(),
            targetObservedAt,
            HIGH_CONFIDENCE_DEVIATION,
            MEDIUM_CONFIDENCE_DEVIATION
        );
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

    /**
     * Phase 18A multi-window trend calculation.
     *
     * <p>Produces the richer {@link TrendMetrics} model with source-aware
     * {@link RawMetricDelta}s, growth rate, velocity, and acceleration. The
     * returned confidence uses source-specific semantics: only
     * {@link MetricSemantics#MONOTONIC_CUMULATIVE} drops flag
     * {@link GrowthConfidence#METRIC_RESET}, so search-rank drift and social
     * counter fluctuation no longer look like pipeline anomalies.
     */
    @Transactional(readOnly = true)
    public TrendMetrics calculateTrend(long hotItemId, TrendWindow window) {
        SignalSnapshotEntity current = signalSnapshotService.latestSnapshot(hotItemId);
        if (current == null) {
            return unknownTrend(hotItemId, window);
        }

        Instant targetObservedAt = current.getObservedAt().minus(window.target());
        List<SignalSnapshotEntity> candidates = signalSnapshotService.listWithinWindow(
            hotItemId,
            targetObservedAt.minus(window.maxDeviation()),
            targetObservedAt.plus(window.maxDeviation())
        );
        SignalSnapshotEntity historical = nearest(targetObservedAt, candidates);
        if (historical == null) {
            return unknownTrend(hotItemId, window);
        }
        if (current.getObservedAt().isBefore(historical.getObservedAt())) {
            return anomalyTrend(hotItemId, window);
        }

        JsonNode currentSignal = current.getNormalizedSignal();
        JsonNode previousSignal = historical.getNormalizedSignal();
        double attentionDelta = metric(currentSignal, "attention") - metric(previousSignal, "attention");
        double discussionDelta = metric(currentSignal, "discussion") - metric(previousSignal, "discussion");
        double adoptionDelta = metric(currentSignal, "adoption") - metric(previousSignal, "adoption");
        double relevanceDelta = metric(currentSignal, "relevance") - metric(previousSignal, "relevance");
        Integer rankDelta = rankDelta(currentSignal, previousSignal);

        SourceType sourceType = current.getSourceType();
        Map<String, MetricSemantics> semantics = semanticsFor(sourceType);
        List<RawMetricDelta> rawDeltas = buildRawDeltas(
            semantics,
            current.getRawMetrics(),
            historical.getRawMetrics()
        );

        GrowthConfidence confidence = confidenceFor(
            historical.getObservedAt(),
            targetObservedAt,
            window.highConfidenceDeviation(),
            window.mediumConfidenceDeviation()
        );
        boolean monotonicReset = rawDeltas.stream().anyMatch(RawMetricDelta::anomaly);
        if (monotonicReset) {
            confidence = GrowthConfidence.METRIC_RESET;
        }

        double momentum = momentumScore(attentionDelta, discussionDelta, adoptionDelta, relevanceDelta, rankDelta);
        Double growthRate = weightedGrowthRate(rawDeltas);
        Double acceleration = accelerationFor(hotItemId, window, current, historical);

        return new TrendMetrics(
            hotItemId,
            window.code(),
            attentionDelta,
            discussionDelta,
            adoptionDelta,
            relevanceDelta,
            rankDelta,
            momentum,
            confidence,
            rawDeltas,
            growthRate,
            // Velocity is the first derivative of normalized momentum. With only
            // two observations we cannot fit a real derivative; we expose the
            // normalized momentum delta itself as the velocity proxy so consumers
            // can read "how fast and in which direction" from a single number.
            normalizedMomentumDelta(attentionDelta, discussionDelta, adoptionDelta, relevanceDelta, rankDelta),
            acceleration,
            current.getObservedAt(),
            historical.getObservedAt(),
            Instant.now()
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

    private TrendMetrics unknownTrend(long hotItemId, TrendWindow window) {
        return new TrendMetrics(
            hotItemId,
            window.code(),
            null, null, null, null, null,
            null,
            GrowthConfidence.UNKNOWN,
            List.of(),
            null,
            null,
            null,
            null, null,
            Instant.now()
        );
    }

    private TrendMetrics anomalyTrend(long hotItemId, TrendWindow window) {
        return new TrendMetrics(
            hotItemId,
            window.code(),
            null, null, null, null, null,
            null,
            GrowthConfidence.DATA_ANOMALY,
            List.of(),
            null,
            null,
            null,
            null, null,
            Instant.now()
        );
    }

    private GrowthConfidence confidenceFor(
        Instant observedAt,
        Instant targetObservedAt,
        Duration highDeviation,
        Duration mediumDeviation
    ) {
        Duration deviation = Duration.between(targetObservedAt, observedAt).abs();
        if (deviation.compareTo(highDeviation) <= 0) {
            return GrowthConfidence.HIGH;
        }
        if (deviation.compareTo(mediumDeviation) <= 0) {
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

    /**
     * Raw normalized momentum delta without clamping or rank bonus.
     *
     * <p>Used as the velocity proxy so consumers can see the actual signed
     * movement of the normalized signal components, including negative values
     * that the clamped {@link #momentumScore} hides.
     */
    private double normalizedMomentumDelta(
        double attentionDelta,
        double discussionDelta,
        double adoptionDelta,
        double relevanceDelta,
        Integer rankDelta
    ) {
        double sum = attentionDelta * 0.25
            + discussionDelta * 0.25
            + adoptionDelta * 0.25
            + relevanceDelta * 0.25;
        if (rankDelta != null) {
            // Rank contribution is bounded so it cannot dominate the delta.
            sum += Math.max(-20.0, Math.min(20.0, rankDelta));
        }
        return sum;
    }

    private double positive(double value) {
        return Math.max(0.0, value);
    }

    /**
     * Builds source-aware raw metric deltas using the adapter's declared
     * {@link MetricSemantics}. Returns an empty list when the adapter has not
     * declared any semantics, which keeps pre-Phase 18A adapters lossless.
     */
    private List<RawMetricDelta> buildRawDeltas(
        Map<String, MetricSemantics> semantics,
        JsonNode currentRaw,
        JsonNode previousRaw
    ) {
        if (semantics.isEmpty()) {
            return List.of();
        }
        if (currentRaw == null || previousRaw == null) {
            return List.of();
        }
        List<RawMetricDelta> deltas = new ArrayList<>(semantics.size());
        List<String> fields = new ArrayList<>(semantics.keySet());
        fields.sort(String::compareTo);
        for (String field : fields) {
            MetricSemantics type = semantics.get(field);
            Double previous = readDouble(previousRaw, field);
            Double current = readDouble(currentRaw, field);
            if (previous == null && current == null) {
                continue;
            }
            Double delta = (previous == null || current == null) ? null : current - previous;
            Double growthRate = growthRate(delta, previous);
            boolean anomaly = isAnomaly(type, previous, current, delta);
            deltas.add(new RawMetricDelta(field, previous, current, delta, growthRate, type, anomaly));
        }
        return deltas;
    }

    private Double readDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asDouble();
    }

    private Double growthRate(Double delta, Double previous) {
        if (delta == null || previous == null || previous <= 0.0) {
            return null;
        }
        double rate = delta / previous;
        if (rate < -1.0) {
            return -1.0;
        }
        return rate;
    }

    private boolean isAnomaly(MetricSemantics semantics, Double previous, Double current, Double delta) {
        if (semantics != MetricSemantics.MONOTONIC_CUMULATIVE) {
            return false;
        }
        if (previous == null || current == null || delta == null) {
            return false;
        }
        // A cumulative counter must never decrease; any drop is a real anomaly.
        return delta < 0.0;
    }

    /**
     * Weighted relative growth across raw deltas, using semantics as weights.
     *
     * <p>{@link MetricSemantics#MONOTONIC_CUMULATIVE} deltas carry the strongest
     * signal (weight 1.0) because their movement is the most meaningful.
     * {@code VOLATILE_SOCIAL} deltas are still informative but noisier (0.6).
     * {@code RANK_LIKE_REVERSIBLE} movement is informational only (0.3).
     * {@code RELEVANCE_SCORE} is excluded from growth rate to avoid amplifying
     * provider-side score drift.
     */
    private Double weightedGrowthRate(List<RawMetricDelta> deltas) {
        if (deltas.isEmpty()) {
            return null;
        }
        double weightedSum = 0.0;
        double weightTotal = 0.0;
        for (RawMetricDelta delta : deltas) {
            if (delta.growthRate() == null) {
                continue;
            }
            double weight = switch (delta.semantics()) {
                case MONOTONIC_CUMULATIVE -> 1.0;
                case VOLATILE_SOCIAL -> 0.6;
                case RANK_LIKE_REVERSIBLE -> 0.3;
                case RELEVANCE_SCORE -> 0.0;
            };
            if (weight <= 0.0) {
                continue;
            }
            weightedSum += delta.growthRate() * weight;
            weightTotal += weight;
        }
        if (weightTotal <= 0.0) {
            return null;
        }
        double rate = weightedSum / weightTotal;
        if (rate < -1.0) {
            return -1.0;
        }
        return rate;
    }

    /**
     * Acceleration proxy: difference between the current window's momentum and
     * the previous equal-sized window's momentum.
     *
     * <p>Requires a third snapshot at {@code current - 2*window}. Returns
     * {@code null} when that snapshot is unavailable so callers cannot mistake
     * absence for zero acceleration.
     */
    private Double accelerationFor(
        long hotItemId,
        TrendWindow window,
        SignalSnapshotEntity current,
        SignalSnapshotEntity historical
    ) {
        Instant previousWindowTarget = historical.getObservedAt().minus(window.target());
        List<SignalSnapshotEntity> previousCandidates = signalSnapshotService.listWithinWindow(
            hotItemId,
            previousWindowTarget.minus(window.maxDeviation()),
            previousWindowTarget.plus(window.maxDeviation())
        );
        SignalSnapshotEntity previousWindow = nearest(previousWindowTarget, previousCandidates);
        if (previousWindow == null) {
            return null;
        }
        if (historical.getObservedAt().isBefore(previousWindow.getObservedAt())) {
            return null;
        }

        double previousMomentum = momentumScore(
            historical.getNormalizedSignal(),
            previousWindow.getNormalizedSignal()
        );
        double currentMomentum = momentumScore(
            current.getNormalizedSignal(),
            historical.getNormalizedSignal()
        );
        return currentMomentum - previousMomentum;
    }

    private double momentumScore(JsonNode currentSignal, JsonNode previousSignal) {
        return momentumScore(
            metric(currentSignal, "attention") - metric(previousSignal, "attention"),
            metric(currentSignal, "discussion") - metric(previousSignal, "discussion"),
            metric(currentSignal, "adoption") - metric(previousSignal, "adoption"),
            metric(currentSignal, "relevance") - metric(previousSignal, "relevance"),
            rankDelta(currentSignal, previousSignal)
        );
    }

    private Map<String, MetricSemantics> semanticsFor(SourceType sourceType) {
        if (!adapterRegistry.hasAdapter(sourceType)) {
            return Map.of();
        }
        SourceSignalAdapter adapter = adapterRegistry.getRequired(sourceType);
        return adapter.metricSemantics();
    }
}
