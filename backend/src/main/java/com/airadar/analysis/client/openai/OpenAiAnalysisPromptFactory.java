package com.airadar.analysis.client.openai;

import com.airadar.analysis.client.ClusterEvidencePack;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the system and user message strings sent to the OpenAI Chat
 * Completions API. {@link #buildInstructions()} becomes the system message
 * and {@link #buildInput(ClusterEvidencePack)} becomes the user message.
 * Centralizing prompt construction keeps the provider client thin and makes
 * prompt changes unit-testable without touching HTTP.
 */
@Component
public class OpenAiAnalysisPromptFactory {

    private static final String INSTRUCTIONS = """
            You are an AI industry intelligence analyst for the AI Radar platform.
            Your job is to produce a structured cluster brief based strictly on the
            evidence pack provided in the input.

            Hard constraints:
            - Use only information present in the supplied evidence. Do not invent
              sources, URLs, authors, numbers, dates, or events.
            - Every entry in evidenceRefs must reuse a hotItemId that appears in
              the input evidence pack, with the matching sourceType, title, and
              sourceUrl.
            - headline, brief, whyItMatters must be grounded in the evidence and
              must not contain speculative claims.
            - keySignals, evidenceRefs, risks, and followUps must each contain at
              least one entry.
            - confidence must be exactly one of: LOW, MEDIUM, HIGH.
              - LOW: single source or thin evidence.
              - MEDIUM: multiple items but limited cross-source corroboration.
              - HIGH: multiple items from at least two distinct source types.

            Output format: respond with a single JSON object and nothing else.
            No markdown fences, no leading or trailing prose. The JSON object
            must match exactly this shape:

            {
              "headline": string,
              "brief": string,
              "whyItMatters": string,
              "keySignals": string[],
              "evidenceRefs": [
                {
                  "hotItemId": number,
                  "sourceType": "ARXIV" | "HACKER_NEWS" | "GITHUB" | "HUGGING_FACE" | "SOGOU_SEARCH" | "WEIBO_HOT_SEARCH" | "HACKER_NEWS_SEARCH" | "TWITTER",
                  "title": string,
                  "sourceUrl": string
                }
              ],
              "risks": string[],
              "followUps": string[],
              "confidence": "LOW" | "MEDIUM" | "HIGH"
            }

            Tone: factual, concise, decision-useful. Avoid marketing language.
            """;

    public String buildInstructions() {
        return INSTRUCTIONS;
    }

    public String buildInput(ClusterEvidencePack evidencePack) {
        StringBuilder builder = new StringBuilder();
        builder.append("Cluster ID: ").append(evidencePack.clusterId()).append('\n');
        appendField(builder, "Title", evidencePack.title());
        appendField(builder, "Summary", evidencePack.summary());
        if (evidencePack.totalScore() != null) {
            builder.append("Total hot score: ").append(evidencePack.totalScore().stripTrailingZeros().toPlainString()).append('\n');
        }
        appendField(builder, "Source types",
                evidencePack.sourceTypes().stream().map(Enum::name).collect(Collectors.joining(", ")));
        if (evidencePack.firstSeenAt() != null) {
            builder.append("First seen at: ").append(evidencePack.firstSeenAt()).append('\n');
        }
        if (evidencePack.lastSeenAt() != null) {
            builder.append("Last seen at: ").append(evidencePack.lastSeenAt()).append('\n');
        }
        builder.append("\nEvidence items:\n");
        List<ClusterEvidencePack.EvidenceItemSnapshot> items = evidencePack.evidenceItems();
        for (int i = 0; i < items.size(); i++) {
            ClusterEvidencePack.EvidenceItemSnapshot item = items.get(i);
            builder.append("--- Evidence #").append(i + 1).append(" ---\n");
            builder.append("hotItemId: ").append(item.hotItemId()).append('\n');
            builder.append("sourceType: ").append(item.sourceType().name()).append('\n');
            appendField(builder, "title", item.title());
            appendField(builder, "summary", item.summary());
            appendField(builder, "sourceUrl", item.sourceUrl());
            appendField(builder, "author", item.author());
            if (item.publishedAt() != null) {
                builder.append("publishedAt: ").append(item.publishedAt()).append('\n');
            }
        }
        return builder.toString();
    }

    private void appendField(StringBuilder builder, String label, String value) {
        builder.append(label).append(": ").append(value == null ? "" : value).append('\n');
    }
}
