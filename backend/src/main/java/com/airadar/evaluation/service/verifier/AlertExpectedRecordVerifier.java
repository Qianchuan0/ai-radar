package com.airadar.evaluation.service.verifier;

import com.airadar.alert.entity.AlertRecordEntity;
import com.airadar.alert.mapper.AlertRecordMapper;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Verifies whether a given subscription rule and cluster produced an
 * {@code alert_record}.
 *
 * <p>Target payload: {@code {"subscriptionRuleId": 1, "hotClusterId": 2}}.
 * Expected payload: {@code {"present": true|false}}.
 */
@Component
public class AlertExpectedRecordVerifier implements CaseVerifier {

    private final AlertRecordMapper alertRecordMapper;
    private final ObjectMapper objectMapper;

    public AlertExpectedRecordVerifier(AlertRecordMapper alertRecordMapper, ObjectMapper objectMapper) {
        this.alertRecordMapper = alertRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationCaseType supportedType() {
        return EvaluationCaseType.ALERT_EXPECTED_RECORD;
    }

    @Override
    public VerificationOutcome verify(EvaluationCaseEntity caseEntity) {
        JsonNode target = caseEntity.getTargetPayload();
        JsonNode expected = caseEntity.getExpectedPayload();

        long subscriptionRuleId = target.path("subscriptionRuleId").asLong(0L);
        long hotClusterId = target.path("hotClusterId").asLong(0L);
        if (subscriptionRuleId <= 0L || hotClusterId <= 0L) {
            return VerificationOutcome.error(
                    "ALERT_EXPECTED_RECORD requires positive target.subscriptionRuleId and target.hotClusterId."
            );
        }
        if (!expected.has("present") || !expected.get("present").isBoolean()) {
            return VerificationOutcome.error(
                    "ALERT_EXPECTED_RECORD requires boolean expected.present."
            );
        }
        boolean expectedPresent = expected.path("present").asBoolean();

        List<AlertRecordEntity> alerts = alertRecordMapper.selectList(
                new LambdaQueryWrapper<AlertRecordEntity>()
                        .eq(AlertRecordEntity::getSubscriptionRuleId, subscriptionRuleId)
                        .eq(AlertRecordEntity::getHotClusterId, hotClusterId)
                        .last("LIMIT 1")
        );
        boolean actuallyPresent = !alerts.isEmpty();
        Long alertId = actuallyPresent ? alerts.get(0).getId() : null;

        ObjectNode actual = objectMapper.createObjectNode();
        actual.put("subscriptionRuleId", subscriptionRuleId);
        actual.put("hotClusterId", hotClusterId);
        actual.put("found", actuallyPresent);
        if (alertId != null) {
            actual.put("alertId", alertId);
        }

        if (actuallyPresent == expectedPresent) {
            return VerificationOutcome.passed(actual);
        }
        return VerificationOutcome.failed(
                actual,
                "Expected alert to be " + (expectedPresent ? "present" : "absent")
                        + " but it was " + (actuallyPresent ? "present" : "absent") + "."
        );
    }
}
