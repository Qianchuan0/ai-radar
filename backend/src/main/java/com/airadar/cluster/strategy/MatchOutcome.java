package com.airadar.cluster.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of running the layered match rules against a single candidate.
 *
 * <p>Each {@code MatchOutcome} reports:
 * <ul>
 *   <li>{@link #decision} — ACCEPTED / REJECTED / REVIEW_REQUIRED</li>
 *   <li>{@link #score} — numeric match score in {@code [0, 1]}</li>
 *   <li>{@link #method} — stable tag ({@code CANONICAL_URL}, {@code ENTITY_TIME},
 *       {@code SIMILARITY}, etc.) persisted as {@code match_method}</li>
 *   <li>{@link #layer} — which match layer produced the outcome ({@code L1},
 *       {@code L2}, {@code L3})</li>
 *   <li>{@link #components} — score breakdown for L3 (empty for L1/L2)</li>
 *   <li>{@link #reasonText} — short human-readable explanation</li>
 * </ul>
 *
 * <p>{@link #NO_MATCH} is the sentinel returned when no candidate was even
 * considered. The V2 strategy treats it as {@link AssignmentDecision#NO_CANDIDATE}.
 */
public final class MatchOutcome {

    public static final MatchOutcome NO_MATCH = new MatchOutcome(
            AssignmentDecision.NO_CANDIDATE,
            BigDecimal.ZERO,
            "NO_MATCH",
            "—",
            Map.of(),
            "no candidate"
    );

    private final AssignmentDecision decision;
    private final BigDecimal score;
    private final String method;
    private final String layer;
    private final Map<String, Double> components;
    private final String reasonText;

    public MatchOutcome(
            AssignmentDecision decision,
            BigDecimal score,
            String method,
            String layer,
            Map<String, Double> components,
            String reasonText
    ) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.score = scaleScore(score);
        this.method = Objects.requireNonNull(method, "method");
        this.layer = Objects.requireNonNull(layer, "layer");
        this.components = Map.copyOf(Objects.requireNonNull(components, "components"));
        this.reasonText = Objects.requireNonNull(reasonText, "reasonText");
    }

    public AssignmentDecision getDecision() {
        return decision;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getMethod() {
        return method;
    }

    public String getLayer() {
        return layer;
    }

    public Map<String, Double> getComponents() {
        return components;
    }

    public String getReasonText() {
        return reasonText;
    }

    /**
     * Serializes the outcome to a JSONB-friendly object node containing
     * everything the {@code cluster_match_decision.match_reason} column needs.
     */
    public JsonNode toReasonJson(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("layer", layer);
        node.put("method", method);
        node.put("score", score);
        node.put("reason", reasonText);
        if (!components.isEmpty()) {
            ObjectNode comps = objectMapper.createObjectNode();
            components.forEach((k, v) -> comps.put(k, BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).doubleValue()));
            node.set("components", comps);
        }
        return node;
    }

    private static BigDecimal scaleScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        return score.setScale(6, RoundingMode.HALF_UP);
    }
}
