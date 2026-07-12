package com.airadar.analysis.client.openai;

import com.airadar.analysis.vo.AnalysisEvidenceRefVO;
import com.airadar.analysis.vo.StructuredAnalysisResultVO;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Maps the SDK-deserialized {@link OpenAiAnalysisOutput} into the canonical
 * {@link StructuredAnalysisResultVO}. Any schema violation (missing field,
 * unknown enum, malformed evidence reference) throws an
 * {@link IllegalArgumentException} that the caller maps to
 * {@code ANALYSIS_RESPONSE_PARSE_FAILED}.
 */
@Component
public class OpenAiAnalysisResponseMapper {

    private static final Set<String> ALLOWED_CONFIDENCE = Set.of("LOW", "MEDIUM", "HIGH");

    public StructuredAnalysisResultVO map(OpenAiAnalysisOutput output) {
        if (output == null) {
            throw new IllegalArgumentException("OpenAI structured output is null.");
        }
        String headline = requireNonBlank(output.headline, "headline");
        String brief = requireNonBlank(output.brief, "brief");
        String whyItMatters = requireNonBlank(output.whyItMatters, "whyItMatters");
        String confidence = normalizeConfidence(output.confidence);
        List<String> keySignals = requireNonEmpty(output.keySignals, "keySignals");
        List<String> risks = requireNonEmpty(output.risks, "risks");
        List<String> followUps = requireNonEmpty(output.followUps, "followUps");
        List<AnalysisEvidenceRefVO> evidenceRefs = mapEvidenceRefs(output.evidenceRefs);

        return new StructuredAnalysisResultVO(
                headline,
                brief,
                whyItMatters,
                keySignals,
                evidenceRefs,
                risks,
                followUps,
                confidence
        );
    }

    private List<AnalysisEvidenceRefVO> mapEvidenceRefs(List<OpenAiAnalysisOutput.EvidenceRef> refs) {
        if (refs == null || refs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs must contain at least one item.");
        }
        return refs.stream().map(this::mapEvidenceRef).toList();
    }

    private AnalysisEvidenceRefVO mapEvidenceRef(OpenAiAnalysisOutput.EvidenceRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("evidenceRef entry is null.");
        }
        if (ref.hotItemId == null) {
            throw new IllegalArgumentException("evidenceRef.hotItemId is required.");
        }
        String sourceTypeRaw = requireNonBlank(ref.sourceType, "evidenceRef.sourceType");
        String title = requireNonBlank(ref.title, "evidenceRef.title");
        String sourceUrl = requireNonBlank(ref.sourceUrl, "evidenceRef.sourceUrl");
        SourceType sourceType = parseSourceType(sourceTypeRaw);
        return new AnalysisEvidenceRefVO(ref.hotItemId, sourceType, title, sourceUrl);
    }

    private SourceType parseSourceType(String value) {
        try {
            return SourceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("evidenceRef.sourceType is not a valid SourceType: " + value);
        }
    }

    private String normalizeConfidence(String value) {
        String normalized = requireNonBlank(value, "confidence").trim().toUpperCase();
        if (!ALLOWED_CONFIDENCE.contains(normalized)) {
            throw new IllegalArgumentException("confidence must be LOW, MEDIUM, or HIGH. Got: " + value);
        }
        return normalized;
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private List<String> requireNonEmpty(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must contain at least one item.");
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }
}
