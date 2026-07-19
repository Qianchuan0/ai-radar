package com.airadar.cluster.strategy;

import com.airadar.cluster.feature.extractor.EntityRef;
import com.airadar.cluster.feature.extractor.ItemFeature;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Layered deterministic matcher used by the V2 clustering strategy.
 *
 * <p>The three layers are evaluated in order; the first layer that produces
 * a non-{@code null} outcome wins. Each layer is independent and uses only
 * the two feature vectors — no extra state, no LLM.
 *
 * <h2>Layer 1 — Deterministic identifiers</h2>
 * <p>Auto-accept when any of the following are equal:
 * <ul>
 *   <li>canonical URL</li>
 *   <li>arXiv id ({@code external_ids.arxiv})</li>
 *   <li>GitHub repo ({@code external_ids.github})</li>
 *   <li>Hugging Face model id ({@code external_ids.hf_model})</li>
 * </ul>
 * <p>Score range: {@code 0.95 - 1.00}.
 *
 * <h2>Layer 2 — Strong entity + organization + event type + time</h2>
 * <p>Auto-accept when ALL hold:
 * <ul>
 *   <li>shared PRODUCT entity</li>
 *   <li>shared organization (via ORG entity or publisher domain match)</li>
 *   <li>compatible event type (same value, or both UNKNOWN)</li>
 *   <li>event time within {@link #LEVEL_2_TIME_WINDOW}</li>
 * </ul>
 * <p>Score: {@code 0.85}. Returns {@code null} (defer to L3) if any condition
 * fails — failures here are not rejections on their own.
 *
 * <h2>Layer 3 — Weighted similarity</h2>
 * <pre>
 *   matchScore = titleSimilarity * 0.30
 *              + entityOverlap     * 0.35
 *              + keywordOverlap    * 0.15
 *              + actionConsistency * 0.15
 *              + timeProximity     * 0.05
 * </pre>
 * <p>Thresholds:
 * <ul>
 *   <li>{@code >= 0.82} → ACCEPTED</li>
 *   <li>{@code < 0.60} → REJECTED</li>
 *   <li>{@code 0.60 .. 0.82} → REVIEW_REQUIRED</li>
 * </ul>
 */
@Component
public class LayeredMatcher {

    public static final double LEVEL_1_SCORE_CANONICAL_URL = 0.97;
    public static final double LEVEL_1_SCORE_EXTERNAL_ID = 0.98;
    public static final double LEVEL_2_SCORE = 0.85;
    public static final double LEVEL_3_ACCEPT_THRESHOLD = 0.82;
    public static final double LEVEL_3_REJECT_THRESHOLD = 0.60;

    public static final Duration LEVEL_2_TIME_WINDOW = Duration.ofHours(48);
    public static final Duration LEVEL_3_TIME_FULL_PROXIMITY = Duration.ofHours(24);

    private static final Map<String, Double> WEIGHTS = Map.of(
            "titleSimilarity", 0.30,
            "entityOverlap", 0.35,
            "keywordOverlap", 0.15,
            "actionConsistency", 0.15,
            "timeProximity", 0.05
    );

    public MatchOutcome match(ItemFeature newItem, ItemFeature candidateItem) {
        if (newItem == null || candidateItem == null) {
            return MatchOutcome.NO_MATCH;
        }

        MatchOutcome level1 = evaluateLevel1(newItem, candidateItem);
        if (level1 != null) {
            return level1;
        }

        MatchOutcome level2 = evaluateLevel2(newItem, candidateItem);
        if (level2 != null) {
            return level2;
        }

        return evaluateLevel3(newItem, candidateItem);
    }

    private MatchOutcome evaluateLevel1(ItemFeature a, ItemFeature b) {
        if (a.getCanonicalUrl() != null
                && !a.getCanonicalUrl().isBlank()
                && a.getCanonicalUrl().equals(b.getCanonicalUrl())) {
            return accepted("CANONICAL_URL", "L1", LEVEL_1_SCORE_CANONICAL_URL,
                    "canonical URL match: " + a.getCanonicalUrl());
        }
        String[] l1Ids = {"arxiv", "github", "hf_model"};
        for (String idKind : l1Ids) {
            String av = a.getExternalIds().get(idKind);
            String bv = b.getExternalIds().get(idKind);
            if (av != null && av.equals(bv)) {
                return accepted(idKindTag(idKind), "L1", LEVEL_1_SCORE_EXTERNAL_ID,
                        idKind + " id match: " + av);
            }
        }
        return null;
    }

    private MatchOutcome evaluateLevel2(ItemFeature a, ItemFeature b) {
        if (sharedProductEntities(a, b).isEmpty()) {
            return null;
        }
        if (!shareOrganization(a, b)) {
            return null;
        }
        if (!eventTypesCompatible(a, b)) {
            return null;
        }
        if (!eventTimesClose(a.getEventTime(), b.getEventTime(), LEVEL_2_TIME_WINDOW)) {
            return null;
        }
        return accepted("ENTITY_TIME", "L2", LEVEL_2_SCORE,
                "shared product + organization + compatible event type + time window");
    }

    private MatchOutcome evaluateLevel3(ItemFeature a, ItemFeature b) {
        double titleSim = jaccard(tokenize(a.getNormalizedTitle()), tokenize(b.getNormalizedTitle()));
        double entityOverlap = entityJaccard(a.getEntities(), b.getEntities());
        double keywordOverlap = jaccardStrings(a.getKeywords(), b.getKeywords());
        double actionConsistency = actionConsistency(a, b);
        double timeProximity = timeProximity(a.getEventTime(), b.getEventTime());

        double score = titleSim * WEIGHTS.get("titleSimilarity")
                + entityOverlap * WEIGHTS.get("entityOverlap")
                + keywordOverlap * WEIGHTS.get("keywordOverlap")
                + actionConsistency * WEIGHTS.get("actionConsistency")
                + timeProximity * WEIGHTS.get("timeProximity");

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("titleSimilarity", titleSim);
        components.put("entityOverlap", entityOverlap);
        components.put("keywordOverlap", keywordOverlap);
        components.put("actionConsistency", actionConsistency);
        components.put("timeProximity", timeProximity);
        components.put("weightedTotal", score);

        BigDecimal scoreBd = BigDecimal.valueOf(score);
        if (score >= LEVEL_3_ACCEPT_THRESHOLD) {
            return new MatchOutcome(AssignmentDecision.ACCEPTED, scoreBd, "SIMILARITY", "L3",
                    components, "L3 similarity above accept threshold");
        }
        if (score < LEVEL_3_REJECT_THRESHOLD) {
            return new MatchOutcome(AssignmentDecision.REJECTED, scoreBd, "SIMILARITY", "L3",
                    components, "L3 similarity below reject threshold");
        }
        return new MatchOutcome(AssignmentDecision.REVIEW_REQUIRED, scoreBd, "SIMILARITY", "L3",
                components, "L3 similarity in review zone");
    }

    private static MatchOutcome accepted(String method, String layer, double score, String reason) {
        return new MatchOutcome(
                AssignmentDecision.ACCEPTED,
                BigDecimal.valueOf(score),
                method,
                layer,
                Map.of(),
                reason
        );
    }

    private static String idKindTag(String idKind) {
        return switch (idKind) {
            case "arxiv" -> "ARXIV_ID";
            case "github" -> "GITHUB_REPO";
            case "hf_model" -> "HF_MODEL_ID";
            default -> idKind.toUpperCase(Locale.ROOT);
        };
    }

    private static Set<String> sharedProductEntities(ItemFeature a, ItemFeature b) {
        Set<String> aProducts = entityValuesOfType(a, EntityRef.Type.PRODUCT);
        Set<String> bProducts = entityValuesOfType(b, EntityRef.Type.PRODUCT);
        aProducts.retainAll(bProducts);
        return aProducts;
    }

    private static Set<String> entityValuesOfType(ItemFeature f, EntityRef.Type type) {
        Set<String> values = new HashSet<>();
        for (EntityRef ref : f.getEntities()) {
            if (ref.getType() == type) {
                values.add(ref.getValue());
            }
        }
        return values;
    }

    private static boolean shareOrganization(ItemFeature a, ItemFeature b) {
        Set<String> aOrgs = entityValuesOfType(a, EntityRef.Type.ORG);
        Set<String> bOrgs = entityValuesOfType(b, EntityRef.Type.ORG);
        if (!aOrgs.isEmpty() && !bOrgs.isEmpty()) {
            aOrgs.retainAll(bOrgs);
            return !aOrgs.isEmpty();
        }
        // Fall back to publisher domain only when entities lack ORG info.
        if (a.getPublisherDomain() != null && b.getPublisherDomain() != null) {
            return a.getPublisherDomain().equals(b.getPublisherDomain());
        }
        return false;
    }

    private static boolean eventTypesCompatible(ItemFeature a, ItemFeature b) {
        // Same event type is obviously compatible. UNKNOWN is treated as
        // neutral so it does not block an otherwise strong L2 match.
        if (a.getEventType() == b.getEventType()) {
            return true;
        }
        return a.getEventType() == com.airadar.cluster.feature.extractor.EventType.UNKNOWN
                || b.getEventType() == com.airadar.cluster.feature.extractor.EventType.UNKNOWN;
    }

    private static boolean eventTimesClose(Instant a, Instant b, Duration window) {
        if (a == null || b == null) {
            return false;
        }
        return Duration.between(a, b).abs().compareTo(window) <= 0;
    }

    private static double actionConsistency(ItemFeature a, ItemFeature b) {
        // Strong bonus when both items carry the same explicit event type;
        // partial credit when either is UNKNOWN; zero when they disagree.
        if (a.getEventType() == b.getEventType()) {
            return 1.0;
        }
        if (a.getEventType() == com.airadar.cluster.feature.extractor.EventType.UNKNOWN
                || b.getEventType() == com.airadar.cluster.feature.extractor.EventType.UNKNOWN) {
            return 0.5;
        }
        return 0.0;
    }

    private static double timeProximity(Instant a, Instant b) {
        if (a == null || b == null) {
            return 0.0;
        }
        Duration delta = Duration.between(a, b).abs();
        // Within the full-proximity window: max score.
        if (delta.compareTo(LEVEL_3_TIME_FULL_PROXIMITY) <= 0) {
            return 1.0;
        }
        // Between 24h and 72h: linear decay from 1.0 to 0.0. Anything beyond
        // 72h is already filtered out by the retriever, so we bottom out at 0.
        // Earlier implementations had two bugs here: (1) the 0-24h branch
        // decayed linearly to 0, contradicting the "full proximity" name and
        // producing a discontinuity at 24h; (2) the 24-72h branch multiplied
        // by 0.0, collapsing the entire band to zero. Both are fixed.
        Duration seventyTwoHours = Duration.ofHours(72);
        if (delta.compareTo(seventyTwoHours) >= 0) {
            return 0.0;
        }
        double beyondFull = delta.minus(LEVEL_3_TIME_FULL_PROXIMITY).toMinutes();
        double remaining = seventyTwoHours.minus(LEVEL_3_TIME_FULL_PROXIMITY).toMinutes();
        return Math.max(0.0, 1.0 - beyondFull / remaining);
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : text.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static double jaccardStrings(List<String> a, List<String> b) {
        return jaccard(new HashSet<>(a), new HashSet<>(b));
    }

    private static double entityJaccard(List<EntityRef> a, List<EntityRef> b) {
        Set<String> aa = new HashSet<>();
        a.forEach(e -> aa.add(e.getType() + ":" + e.getValue()));
        Set<String> bb = new HashSet<>();
        b.forEach(e -> bb.add(e.getType() + ":" + e.getValue()));
        return jaccard(aa, bb);
    }
}
