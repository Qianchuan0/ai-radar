package com.airadar.evaluation.service.verifier;

import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.airadar.scoring.service.RuleBasedScoringService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Verifies that the latest {@code hot_score} for a cluster satisfies the labeled
 * total-score bounds and optional per-component bounds.
 *
 * <p>Target payload: {@code {"hotClusterId": 1}}.
 * Expected payload (any subset is evaluated):
 * {@code {"minTotalScore": 50, "maxTotalScore": 100, "component": "freshness", "minComponentScore": 10}}.
 */
@Component
public class ScoreThresholdVerifier implements CaseVerifier {

    private final HotScoreMapper hotScoreMapper;
    private final ObjectMapper objectMapper;

    public ScoreThresholdVerifier(HotScoreMapper hotScoreMapper, ObjectMapper objectMapper) {
        this.hotScoreMapper = hotScoreMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationCaseType supportedType() {
        return EvaluationCaseType.SCORE_THRESHOLD;
    }

    @Override
    public VerificationOutcome verify(EvaluationCaseEntity caseEntity) {
        JsonNode target = caseEntity.getTargetPayload();
        JsonNode expected = caseEntity.getExpectedPayload();

        long clusterId = target.path("hotClusterId").asLong(0L);
        if (clusterId <= 0L) {
            return VerificationOutcome.error(
                    "SCORE_THRESHOLD requires a positive target.hotClusterId."
            );
        }

        List<HotScoreEntity> scores = hotScoreMapper.selectList(
                new LambdaQueryWrapper<HotScoreEntity>()
                        .eq(HotScoreEntity::getHotClusterId, clusterId)
                        .eq(HotScoreEntity::getScoringVersion, RuleBasedScoringService.SCORING_VERSION)
                        .orderByDesc(HotScoreEntity::getCalculatedAt)
                        .orderByDesc(HotScoreEntity::getId)
                        .last("LIMIT 1")
        );
        if (scores.isEmpty()) {
            return VerificationOutcome.error(
                    "No hot_score found for cluster " + clusterId + "."
            );
        }
        HotScoreEntity score = scores.get(0);

        ObjectNode actual = objectMapper.createObjectNode();
        actual.put("hotClusterId", clusterId);
        actual.put("scoreId", score.getId());
        if (score.getTotalScore() != null) {
            actual.put("totalScore", score.getTotalScore().doubleValue());
        }
        if (score.getScoreComponents() != null) {
            actual.set("components", score.getScoreComponents());
        }
        actual.put("scoringVersion", score.getScoringVersion());

        BigDecimal minTotal = expected.has("minTotalScore") && expected.get("minTotalScore").isNumber()
                ? expected.get("minTotalScore").decimalValue()
                : null;
        BigDecimal maxTotal = expected.has("maxTotalScore") && expected.get("maxTotalScore").isNumber()
                ? expected.get("maxTotalScore").decimalValue()
                : null;
        String component = expected.path("component").asText();
        BigDecimal minComponent = expected.has("minComponentScore") && expected.get("minComponentScore").isNumber()
                ? expected.get("minComponentScore").decimalValue()
                : null;

        if (minTotal != null && score.getTotalScore() != null
                && score.getTotalScore().compareTo(minTotal) < 0) {
            return VerificationOutcome.failed(
                    actual,
                    "Total score " + score.getTotalScore() + " is below minTotalScore " + minTotal + "."
            );
        }
        if (maxTotal != null && score.getTotalScore() != null
                && score.getTotalScore().compareTo(maxTotal) > 0) {
            return VerificationOutcome.failed(
                    actual,
                    "Total score " + score.getTotalScore() + " exceeds maxTotalScore " + maxTotal + "."
            );
        }
        if (!component.isBlank() && minComponent != null) {
            JsonNode componentValue = score.getScoreComponents() == null
                    ? null
                    : score.getScoreComponents().get(component);
            if (componentValue == null || !componentValue.isNumber()) {
                return VerificationOutcome.failed(
                        actual,
                        "Component '" + component + "' is missing or not numeric in stored score."
                );
            }
            BigDecimal actualComponent = componentValue.decimalValue();
            if (actualComponent.compareTo(minComponent) < 0) {
                return VerificationOutcome.failed(
                        actual,
                        "Component '" + component + "' value " + actualComponent
                                + " is below minComponentScore " + minComponent + "."
                );
            }
        }

        return VerificationOutcome.passed(actual);
    }
}
