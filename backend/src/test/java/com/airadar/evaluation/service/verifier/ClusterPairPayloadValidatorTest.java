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
 * Unit tests for {@link ClusterPairPayloadValidator}. Covers the required
 * fields, allowed enum values, and the {@code hotItemId}-or-{@code (sourceType,
 * externalId)} fallback used by real-data sample import.
 */
class ClusterPairPayloadValidatorTest {

    private ClusterPairPayloadValidator validator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        validator = new ClusterPairPayloadValidator();
        mapper = new ObjectMapper();
    }

    @Test
    void supportedTypeIsClusterPairExpectation() {
        assertThat(validator.supportedType())
                .isEqualTo(EvaluationCaseType.CLUSTER_PAIR_EXPECTATION);
    }

    @Test
    void acceptsPayloadWithHotItemId() {
        ObjectNode target = baseTargetWithHotItemIds();
        ObjectNode expected = baseExpected("MUST_MERGE", "MODEL_RELEASE");

        assertThatCode(() -> validator.validate(target, expected))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsPayloadWithSourceTypeAndExternalIdFallback() {
        ObjectNode target = mapper.createObjectNode();
        target.put("pairKey", "pair-1");
        ObjectNode itemA = mapper.createObjectNode();
        itemA.put("sourceType", "HACKER_NEWS");
        itemA.put("externalId", "39241938");
        itemA.put("title", "OpenAI launches GPT-5");
        ObjectNode itemB = mapper.createObjectNode();
        itemB.put("sourceType", "ARXIV");
        itemB.put("externalId", "2507.12345");
        itemB.put("title", "GPT-5 Technical Report");
        target.set("itemA", itemA);
        target.set("itemB", itemB);

        ObjectNode expected = baseExpected("MUST_MERGE", "MODEL_RELEASE");

        assertThatCode(() -> validator.validate(target, expected))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPayloadMissingPairKey() {
        ObjectNode target = baseTargetWithHotItemIds();
        target.remove("pairKey");
        ObjectNode expected = baseExpected("MUST_MERGE", "MODEL_RELEASE");

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("target_payload.pairKey");
    }

    @Test
    void rejectsItemMissingBothHotItemIdAndExternalId() {
        ObjectNode target = baseTargetWithHotItemIds();
        ObjectNode badItem = mapper.createObjectNode();
        badItem.put("title", "no identifiers at all");
        target.set("itemA", badItem);

        ObjectNode expected = baseExpected("MUST_MERGE", "MODEL_RELEASE");

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("target_payload.itemA");
    }

    @Test
    void rejectsItemMissingTitle() {
        ObjectNode target = baseTargetWithHotItemIds();
        ObjectNode itemA = (ObjectNode) target.get("itemA");
        itemA.remove("title");

        ObjectNode expected = baseExpected("MUST_MERGE", "MODEL_RELEASE");

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("target_payload.itemA.title");
    }

    @Test
    void rejectsUnknownExpectation() {
        ObjectNode target = baseTargetWithHotItemIds();
        ObjectNode expected = baseExpected("SHOULD_MERGE", "MODEL_RELEASE");

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expected_payload.expectation");
    }

    @Test
    void rejectsUnknownCategory() {
        ObjectNode target = baseTargetWithHotItemIds();
        ObjectNode expected = baseExpected("MUST_MERGE", "LAUNCH_EVENT");

        assertThatThrownBy(() -> validator.validate(target, expected))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expected_payload.category");
    }

    @Test
    void acceptsReviewIfAmbiguousExpectation() {
        ObjectNode target = baseTargetWithHotItemIds();
        ObjectNode expected = baseExpected("REVIEW_IF_AMBIGUOUS", "OTHER");

        assertThatCode(() -> validator.validate(target, expected))
                .doesNotThrowAnyException();
    }

    private ObjectNode baseTargetWithHotItemIds() {
        ObjectNode target = mapper.createObjectNode();
        target.put("pairKey", "openai-gpt5-39241938-2507.12345");

        ObjectNode itemA = mapper.createObjectNode();
        itemA.put("hotItemId", 12345L);
        itemA.put("sourceType", "HACKER_NEWS");
        itemA.put("externalId", "39241938");
        itemA.put("title", "OpenAI launches GPT-5");

        ObjectNode itemB = mapper.createObjectNode();
        itemB.put("hotItemId", 12347L);
        itemB.put("sourceType", "ARXIV");
        itemB.put("externalId", "2507.12345");
        itemB.put("title", "GPT-5 Technical Report");

        target.set("itemA", itemA);
        target.set("itemB", itemB);
        return target;
    }

    private ObjectNode baseExpected(String expectation, String category) {
        ObjectNode expected = mapper.createObjectNode();
        expected.put("expectation", expectation);
        expected.put("category", category);
        expected.put("rationale", "annotation rationale");
        expected.put("annotator", "yi");
        return expected;
    }
}
