package com.airadar.evaluation.service.verifier;

import com.airadar.common.exception.BusinessException;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RankingRelevancePayloadValidator}. Covers required
 * fields, ISO-8601 parsing, allowed relevance enum, and the
 * {@code isMajorEvent} boolean check.
 */
class RankingRelevancePayloadValidatorTest {

    private RankingRelevancePayloadValidator validator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        validator = new RankingRelevancePayloadValidator();
        mapper = new ObjectMapper();
    }

    @Test
    void supportedTypeIsRankingRelevanceExpectation() {
        assertThat(validator.supportedType())
                .isEqualTo(EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION);
    }

    @Test
    void acceptsValidPayload() {
        ObjectNode target = baseTarget();
        ObjectNode expected = baseExpected("HIGHLY_RELEVANT", true);

        assertThatCode(() -> validator.validate(target, expected))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonIso8601WindowStart() {
        ObjectNode target = baseTarget();
        target.put("windowStart", "2026/07/18 00:00:00");
        ObjectNode expected = baseExpected("RELEVANT", false);

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("target_payload.windowStart");
    }

    @Test
    void rejectsNonPositiveClusterId() {
        ObjectNode target = baseTarget();
        target.put("clusterId", 0);
        ObjectNode expected = baseExpected("RELEVANT", false);

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("target_payload.clusterId");
    }

    @Test
    void rejectsMissingClusterId() {
        ObjectNode target = baseTarget();
        target.remove("clusterId");
        ObjectNode expected = baseExpected("RELEVANT", false);

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("target_payload.clusterId");
    }

    @Test
    void rejectsUnknownRelevance() {
        ObjectNode target = baseTarget();
        ObjectNode expected = baseExpected("BREAKING", true);

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expected_payload.relevance");
    }

    @Test
    void rejectsNonBooleanIsMajorEvent() {
        ObjectNode target = baseTarget();
        ObjectNode expected = baseExpected("RELEVANT", false);
        expected.put("isMajorEvent", "yes");

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expected_payload.isMajorEvent");
    }

    @Test
    void acceptsAllRelevanceLevels() {
        for (String relevance : new String[]{"HIGHLY_RELEVANT", "RELEVANT", "MARGINALLY_RELEVANT", "NOISE"}) {
            ObjectNode target = baseTarget();
            ObjectNode expected = baseExpected(relevance, false);

            assertThatCode(() -> validator.validate(target, expected))
                    .doesNotThrowAnyException();
        }
    }

    private ObjectNode baseTarget() {
        ObjectNode target = mapper.createObjectNode();
        target.put("windowStart", "2026-07-18T00:00:00Z");
        target.put("windowEnd", "2026-07-18T23:59:59Z");
        target.put("clusterId", 8801L);
        target.putNull("query");
        target.put("snapshotSource", "hot_cluster_at_window_end");
        return target;
    }

    private ObjectNode baseExpected(String relevance, boolean isMajorEvent) {
        ObjectNode expected = mapper.createObjectNode();
        expected.put("relevance", relevance);
        expected.put("isMajorEvent", isMajorEvent);
        expected.put("rationale", "annotation rationale");
        expected.put("annotator", "yi");
        return expected;
    }
}
