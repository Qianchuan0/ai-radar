package com.airadar.evaluation.service.verifier;

import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Verifies whether a given {@code sourceType + externalId} is present in the
 * normalized {@code hot_item} table.
 *
 * <p>Target payload: {@code {"sourceType": "HACKER_NEWS", "externalId": "301"}}.
 * Expected payload: {@code {"present": true|false}}.
 */
@Component
public class CrawlItemPresentVerifier implements CaseVerifier {

    private final HotItemMapper hotItemMapper;
    private final ObjectMapper objectMapper;

    public CrawlItemPresentVerifier(HotItemMapper hotItemMapper, ObjectMapper objectMapper) {
        this.hotItemMapper = hotItemMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationCaseType supportedType() {
        return EvaluationCaseType.CRAWL_ITEM_PRESENT;
    }

    @Override
    public VerificationOutcome verify(EvaluationCaseEntity caseEntity) {
        JsonNode target = caseEntity.getTargetPayload();
        JsonNode expected = caseEntity.getExpectedPayload();

        SourceType sourceType = readSourceType(target);
        String externalId = target.path("externalId").asText();
        if (sourceType == null || externalId.isBlank()) {
            return VerificationOutcome.error(
                    "CRAWL_ITEM_PRESENT requires target.sourceType and target.externalId."
            );
        }
        if (!expected.has("present") || !expected.get("present").isBoolean()) {
            return VerificationOutcome.error(
                    "CRAWL_ITEM_PRESENT requires boolean expected.present."
            );
        }
        boolean expectedPresent = expected.path("present").asBoolean();

        List<HotItemEntity> matches = hotItemMapper.selectList(
                new LambdaQueryWrapper<HotItemEntity>()
                        .eq(HotItemEntity::getSourceType, sourceType)
                        .eq(HotItemEntity::getExternalId, externalId)
                        .last("LIMIT 1")
        );
        boolean actuallyPresent = !matches.isEmpty();
        Long hotItemId = actuallyPresent ? matches.get(0).getId() : null;

        ObjectNode actual = objectMapper.createObjectNode();
        actual.put("sourceType", sourceType.name());
        actual.put("externalId", externalId);
        actual.put("found", actuallyPresent);
        if (hotItemId != null) {
            actual.put("hotItemId", hotItemId);
        }

        if (actuallyPresent == expectedPresent) {
            return VerificationOutcome.passed(actual);
        }
        return VerificationOutcome.failed(
                actual,
                "Expected item to be " + (expectedPresent ? "present" : "absent")
                        + " but it was " + (actuallyPresent ? "present" : "absent") + "."
        );
    }

    private SourceType readSourceType(JsonNode target) {
        String raw = target.path("sourceType").asText();
        if (raw.isBlank()) {
            return null;
        }
        try {
            return SourceType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
