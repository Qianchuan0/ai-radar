package com.airadar.analysis.client.openai;

import com.airadar.analysis.client.ClusterEvidencePack;
import com.airadar.source.model.SourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiAnalysisPromptFactoryTest {

    private final OpenAiAnalysisPromptFactory factory = new OpenAiAnalysisPromptFactory();

    @Test
    void instructionsShouldEnforceEvidenceGroundingAndConfidenceEnum() {
        String instructions = factory.buildInstructions();

        assertThat(instructions).contains("evidence");
        assertThat(instructions).contains("evidenceRefs");
        assertThat(instructions).contains("LOW", "MEDIUM", "HIGH");
        assertThat(instructions).contains("confidence");
    }

    @Test
    void instructionsShouldListEverySupportedSourceType() {
        String instructions = factory.buildInstructions();

        assertThat(SourceType.values())
                .allSatisfy(sourceType -> assertThat(instructions).contains("\"" + sourceType.name() + "\""));
    }

    @Test
    void inputShouldContainClusterMetadataAndEvidenceItems() {
        ClusterEvidencePack.EvidenceItemSnapshot first = new ClusterEvidencePack.EvidenceItemSnapshot(
                101L,
                SourceType.HACKER_NEWS,
                "OpenAI launches an agent framework",
                "Developer discussion around a new agent SDK.",
                "https://example.com/agent",
                "alice",
                Instant.parse("2026-07-10T10:00:00Z")
        );
        ClusterEvidencePack.EvidenceItemSnapshot second = new ClusterEvidencePack.EvidenceItemSnapshot(
                202L,
                SourceType.GITHUB,
                "agent-framework repo",
                null,
                "https://github.com/example/agent-framework",
                null,
                Instant.parse("2026-07-11T08:30:00Z")
        );
        ClusterEvidencePack pack = new ClusterEvidencePack(
                7L,
                "Agent framework cluster",
                "Cluster summary text.",
                new BigDecimal("87.5"),
                List.of(SourceType.HACKER_NEWS, SourceType.GITHUB),
                List.of(first, second),
                Instant.parse("2026-07-10T10:00:00Z"),
                Instant.parse("2026-07-11T08:30:00Z")
        );

        String input = factory.buildInput(pack);

        assertThat(input).contains("Cluster ID: 7");
        assertThat(input).contains("Title: Agent framework cluster");
        assertThat(input).contains("Summary: Cluster summary text.");
        assertThat(input).contains("Total hot score: 87.5");
        assertThat(input).contains("HACKER_NEWS, GITHUB");
        assertThat(input).contains("hotItemId: 101");
        assertThat(input).contains("sourceType: HACKER_NEWS");
        assertThat(input).contains("sourceUrl: https://example.com/agent");
        assertThat(input).contains("hotItemId: 202");
        assertThat(input).contains("sourceType: GITHUB");
        assertThat(input).contains("Evidence #1");
        assertThat(input).contains("Evidence #2");
    }

    @Test
    void inputShouldTolerateNullOptionalFields() {
        ClusterEvidencePack pack = new ClusterEvidencePack(
                1L,
                null,
                null,
                null,
                List.of(SourceType.ARXIV),
                List.of(new ClusterEvidencePack.EvidenceItemSnapshot(
                        9L, SourceType.ARXIV, "title", null, null, null, null
                )),
                null,
                null
        );

        String input = factory.buildInput(pack);

        assertThat(input).contains("Cluster ID: 1");
        assertThat(input).contains("Title: ");
        assertThat(input).contains("hotItemId: 9");
    }
}
