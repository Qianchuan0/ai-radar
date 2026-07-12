package com.airadar.analysis.client.openai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiAnalysisResponseMapperTest {

    private final OpenAiAnalysisResponseMapper mapper = new OpenAiAnalysisResponseMapper();

    @Test
    void shouldMapValidOutputToStructuredResult() {
        OpenAiAnalysisOutput output = baseOutput();

        var result = mapper.map(output);

        assertThat(result.headline()).isEqualTo("Agent framework launch");
        assertThat(result.brief()).contains("evidence-backed brief");
        assertThat(result.whyItMatters()).contains("signal");
        assertThat(result.keySignals()).hasSize(2);
        assertThat(result.evidenceRefs()).hasSize(2);
        assertThat(result.evidenceRefs().get(0).hotItemId()).isEqualTo(101L);
        assertThat(result.evidenceRefs().get(0).sourceType().name()).isEqualTo("HACKER_NEWS");
        assertThat(result.risks()).hasSize(1);
        assertThat(result.followUps()).hasSize(1);
        assertThat(result.confidence()).isEqualTo("HIGH");
    }

    @Test
    void shouldNormalizeLowercaseConfidence() {
        OpenAiAnalysisOutput output = baseOutput();
        output.confidence = "medium";

        assertThat(mapper.map(output).confidence()).isEqualTo("MEDIUM");
    }

    @Test
    void shouldRejectUnknownConfidenceValue() {
        OpenAiAnalysisOutput output = baseOutput();
        output.confidence = "CERTAIN";

        assertThatThrownBy(() -> mapper.map(output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void shouldRejectBlankRequiredField() {
        OpenAiAnalysisOutput output = baseOutput();
        output.headline = "   ";

        assertThatThrownBy(() -> mapper.map(output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headline");
    }

    @Test
    void shouldRejectEmptyEvidenceRefs() {
        OpenAiAnalysisOutput output = baseOutput();
        output.evidenceRefs = List.of();

        assertThatThrownBy(() -> mapper.map(output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceRefs");
    }

    @Test
    void shouldRejectInvalidSourceType() {
        OpenAiAnalysisOutput output = baseOutput();
        output.evidenceRefs.get(0).sourceType = "REDDIT";

        assertThatThrownBy(() -> mapper.map(output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SourceType");
    }

    @Test
    void shouldRejectMissingHotItemId() {
        OpenAiAnalysisOutput output = baseOutput();
        output.evidenceRefs.get(0).hotItemId = null;

        assertThatThrownBy(() -> mapper.map(output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hotItemId");
    }

    @Test
    void shouldRejectNullOutput() {
        assertThatThrownBy(() -> mapper.map(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    private OpenAiAnalysisOutput baseOutput() {
        OpenAiAnalysisOutput output = new OpenAiAnalysisOutput();
        output.headline = "Agent framework launch";
        output.brief = "An evidence-backed brief.";
        output.whyItMatters = "This signal matters for agent ecosystem watchers.";
        output.keySignals = List.of("Two source families cover the event.", "Both items appeared this week.");
        output.risks = List.of("Single-day window may shift after the next crawl.");
        output.followUps = List.of("Compare the next crawl against this cluster.");
        output.confidence = "HIGH";

        OpenAiAnalysisOutput.EvidenceRef ref1 = new OpenAiAnalysisOutput.EvidenceRef();
        ref1.hotItemId = 101L;
        ref1.sourceType = "HACKER_NEWS";
        ref1.title = "OpenAI launches an agent framework";
        ref1.sourceUrl = "https://example.com/agent";

        OpenAiAnalysisOutput.EvidenceRef ref2 = new OpenAiAnalysisOutput.EvidenceRef();
        ref2.hotItemId = 202L;
        ref2.sourceType = "GITHUB";
        ref2.title = "agent-framework repo";
        ref2.sourceUrl = "https://github.com/example/agent-framework";

        output.evidenceRefs = List.of(ref1, ref2);
        return output;
    }
}
