package com.airadar.analysis.client.openai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Schema definition for OpenAI Structured Outputs.
 *
 * <p>Public fields are intentionally used so that the openai-java SDK can derive
 * a strict JSON Schema from this class. Field names map 1:1 to
 * {@link com.airadar.analysis.vo.StructuredAnalysisResultVO} components.</p>
 */
@JsonClassDescription("Structured analysis output for a hot cluster, grounded strictly in the provided evidence.")
public class OpenAiAnalysisOutput {

    @JsonPropertyDescription("Concise headline naming the event. Must be derived only from the supplied evidence.")
    public String headline;

    @JsonPropertyDescription("Two-to-three sentence factual brief summarizing what happened. No speculation.")
    public String brief;

    @JsonPropertyDescription("Why this cluster matters for AI industry watchers. Grounded in evidence only.")
    public String whyItMatters;

    @JsonPropertyDescription("Three to six concrete signals observed in the evidence. Each entry must be a single sentence.")
    public List<String> keySignals;

    @JsonPropertyDescription("Evidence references. Each entry must point to an evidence item that was supplied in the input.")
    public List<EvidenceRef> evidenceRefs;

    @JsonPropertyDescription("Risks, caveats, or open questions that follow from the evidence.")
    public List<String> risks;

    @JsonPropertyDescription("Concrete follow-up actions a watcher could take.")
    public List<String> followUps;

    @JsonPropertyDescription("Analyst confidence in the cluster narrative. Must be one of LOW, MEDIUM, or HIGH.")
    public String confidence;

    public static class EvidenceRef {

        @JsonPropertyDescription("Identifier of the referenced hot item.")
        public Long hotItemId;

        @JsonPropertyDescription("Source type of the referenced evidence, matching the input.")
        public String sourceType;

        @JsonPropertyDescription("Title of the referenced evidence item.")
        public String title;

        @JsonPropertyDescription("Source URL of the referenced evidence item.")
        public String sourceUrl;
    }
}
