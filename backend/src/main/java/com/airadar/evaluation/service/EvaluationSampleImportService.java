package com.airadar.evaluation.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.evaluation.service.verifier.EvaluationPayloadValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Imports Phase 17A real-data annotations from JSONL files into
 * {@code evaluation_case}.
 *
 * <p>The service is idempotent on {@code (dataset_id, case_code)}: each line
 * resolves to a deterministic case code ({@code pairKey} for cluster-pair
 * cases, {@code windowStart|clusterId} for ranking cases), and lines whose
 * case code already exists in the target dataset are skipped. Single-line
 * failures do not abort the batch; errors are collected and reported.
 *
 * <p>File formats (one JSON object per line, blank lines skipped):
 *
 * <p><b>Cluster pair annotation</b>:
 * <pre>
 * {
 *   "pairKey": "openai-gpt5-39241938-2507.12345",
 *   "itemA": { "hotItemId": 12345, "sourceType": "HACKER_NEWS", "externalId": "39241938", "title": "..." },
 *   "itemB": { "hotItemId": 12347, "sourceType": "ARXIV", "externalId": "2507.12345", "title": "..." },
 *   "expectation": "MUST_MERGE",
 *   "category": "MODEL_RELEASE",
 *   "rationale": "...",
 *   "annotator": "yi"
 * }
 * </pre>
 *
 * <p><b>Ranking relevance annotation</b>:
 * <pre>
 * {
 *   "windowStart": "2026-07-18T00:00:00Z",
 *   "windowEnd": "2026-07-18T23:59:59Z",
 *   "clusterId": 8801,
 *   "relevance": "HIGHLY_RELEVANT",
 *   "isMajorEvent": true,
 *   "rationale": "...",
 *   "annotator": "yi"
 * </pre>
 */
@Service
public class EvaluationSampleImportService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationSampleImportService.class);
    private static final int MAX_ERRORS_REPORTED = 50;

    private final EvaluationCaseMapper caseMapper;
    private final ObjectMapper objectMapper;
    private final Map<EvaluationCaseType, EvaluationPayloadValidator> validators;

    public EvaluationSampleImportService(
            EvaluationCaseMapper caseMapper,
            ObjectMapper objectMapper,
            List<EvaluationPayloadValidator> validatorBeans
    ) {
        this.caseMapper = caseMapper;
        this.objectMapper = objectMapper;
        Map<EvaluationCaseType, EvaluationPayloadValidator> registry =
                new EnumMap<>(EvaluationCaseType.class);
        for (EvaluationPayloadValidator validator : validatorBeans) {
            registry.put(validator.supportedType(), validator);
        }
        this.validators = registry;
    }

    @Transactional
    public ImportResult importClusterPairs(long datasetId, Path jsonlPath) {
        EvaluationPayloadValidator validator =
                requireValidator(EvaluationCaseType.CLUSTER_PAIR_EXPECTATION);
        List<String> lines = readLines(jsonlPath);
        int totalLines = 0;
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            totalLines++;
            int lineNumber = i + 1;
            try {
                JsonNode line = objectMapper.readTree(raw);
                ObjectNode target = buildPairTarget(line);
                ObjectNode expected = buildPairExpected(line);
                validator.validate(target, expected);

                String caseCode = line.path("pairKey").asText();
                if (caseCode.isBlank()) {
                    throw new IllegalArgumentException("pairKey is empty");
                }
                if (caseExists(datasetId, caseCode)) {
                    skipped++;
                    continue;
                }
                insertCase(datasetId, caseCode, EvaluationCaseType.CLUSTER_PAIR_EXPECTATION,
                        target, expected, line, now);
                imported++;
            } catch (Exception ex) {
                failed++;
                recordError(errors, lineNumber, raw, ex.getMessage());
            }
        }
        logImportOutcome(jsonlPath, "CLUSTER_PAIR_EXPECTATION", imported, skipped, failed);
        return new ImportResult(totalLines, imported, skipped, failed, errors);
    }

    @Transactional
    public ImportResult importRankingRelevance(long datasetId, Path jsonlPath) {
        EvaluationPayloadValidator validator =
                requireValidator(EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION);
        List<String> lines = readLines(jsonlPath);
        int totalLines = 0;
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            totalLines++;
            int lineNumber = i + 1;
            try {
                JsonNode line = objectMapper.readTree(raw);
                ObjectNode target = buildRankingTarget(line);
                ObjectNode expected = buildRankingExpected(line);
                validator.validate(target, expected);

                String windowStart = line.path("windowStart").asText();
                long clusterId = line.path("clusterId").asLong();
                String caseCode = windowStart + "|" + clusterId;
                if (windowStart.isBlank() || clusterId <= 0) {
                    throw new IllegalArgumentException("windowStart and clusterId are required");
                }
                if (caseExists(datasetId, caseCode)) {
                    skipped++;
                    continue;
                }
                insertCase(datasetId, caseCode, EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION,
                        target, expected, line, now);
                imported++;
            } catch (Exception ex) {
                failed++;
                recordError(errors, lineNumber, raw, ex.getMessage());
            }
        }
        logImportOutcome(jsonlPath, "RANKING_RELEVANCE_EXPECTATION", imported, skipped, failed);
        return new ImportResult(totalLines, imported, skipped, failed, errors);
    }

    private EvaluationPayloadValidator requireValidator(EvaluationCaseType type) {
        EvaluationPayloadValidator validator = validators.get(type);
        if (validator == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "No payload validator registered for " + type);
        }
        return validator;
    }

    private void insertCase(
            long datasetId,
            String caseCode,
            EvaluationCaseType caseType,
            ObjectNode target,
            ObjectNode expected,
            JsonNode line,
            Instant now
    ) {
        EvaluationCaseEntity entity = new EvaluationCaseEntity();
        entity.setDatasetId(datasetId);
        entity.setCaseCode(caseCode);
        entity.setCaseType(caseType);
        entity.setTargetPayload(target);
        entity.setExpectedPayload(expected);
        entity.setNotes(buildNotes(line));
        entity.setEnabled(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        caseMapper.insert(entity);
    }

    private boolean caseExists(long datasetId, String caseCode) {
        long count = caseMapper.selectCount(
                new LambdaQueryWrapper<EvaluationCaseEntity>()
                        .eq(EvaluationCaseEntity::getDatasetId, datasetId)
                        .eq(EvaluationCaseEntity::getCaseCode, caseCode)
        );
        return count > 0;
    }

    private List<String> readLines(Path jsonlPath) {
        try {
            return Files.readAllLines(jsonlPath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to read " + jsonlPath + ": " + ex.getMessage());
        }
    }

    private static void recordError(List<String> errors, int lineNumber, String raw, String reason) {
        if (errors.size() >= MAX_ERRORS_REPORTED) {
            return;
        }
        String preview = raw.length() > 120 ? raw.substring(0, 120) + "..." : raw;
        errors.add("line " + lineNumber + ": " + reason + " | " + preview);
    }

    private static void logImportOutcome(
            Path jsonlPath, String type, int imported, int skipped, int failed) {
        if (failed > 0) {
            log.warn("Import of {} from {}: imported={}, skipped={}, failed={}",
                    type, jsonlPath, imported, skipped, failed);
        } else {
            log.info("Import of {} from {}: imported={}, skipped={}",
                    type, jsonlPath, imported, skipped);
        }
    }

    private static String buildNotes(JsonNode line) {
        String annotator = line.path("annotator").isMissingNode() ? "" : line.path("annotator").asText();
        String rationale = line.path("rationale").isMissingNode() ? "" : line.path("rationale").asText();
        if (annotator.isBlank() && rationale.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (!annotator.isBlank()) {
            sb.append("annotator=").append(annotator);
        }
        if (!rationale.isBlank()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append("rationale=").append(rationale);
        }
        return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
    }

    private ObjectNode buildPairTarget(JsonNode line) {
        ObjectNode target = objectMapper.createObjectNode();
        target.put("pairKey", line.path("pairKey").asText());
        target.set("itemA", line.path("itemA"));
        target.set("itemB", line.path("itemB"));
        return target;
    }

    private ObjectNode buildPairExpected(JsonNode line) {
        ObjectNode expected = objectMapper.createObjectNode();
        expected.put("expectation", line.path("expectation").asText());
        expected.put("category", line.path("category").asText());
        copyTextIfPresent(line, expected, "rationale");
        copyTextIfPresent(line, expected, "annotator");
        copyTextIfPresent(line, expected, "annotatedAt");
        return expected;
    }

    private ObjectNode buildRankingTarget(JsonNode line) {
        ObjectNode target = objectMapper.createObjectNode();
        target.put("windowStart", line.path("windowStart").asText());
        target.put("windowEnd", line.path("windowEnd").asText());
        target.put("clusterId", line.path("clusterId").asLong());
        if (!line.path("query").isMissingNode()) {
            target.set("query", line.path("query"));
        }
        if (!line.path("snapshotSource").isMissingNode()) {
            target.put("snapshotSource", line.path("snapshotSource").asText());
        }
        return target;
    }

    private ObjectNode buildRankingExpected(JsonNode line) {
        ObjectNode expected = objectMapper.createObjectNode();
        expected.put("relevance", line.path("relevance").asText());
        expected.put("isMajorEvent", line.path("isMajorEvent").asBoolean());
        if (!line.path("expectedMinRank").isMissingNode()) {
            expected.put("expectedMinRank", line.path("expectedMinRank").asInt());
        }
        copyTextIfPresent(line, expected, "rationale");
        copyTextIfPresent(line, expected, "annotator");
        return expected;
    }

    private static void copyTextIfPresent(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.path(field);
        if (!value.isMissingNode() && !value.isNull()) {
            target.set(field, value);
        }
    }

    /**
     * Summary of one JSONL import run.
     *
     * @param totalLines    non-blank lines parsed
     * @param importedCases new cases inserted
     * @param skippedCases  lines whose case code already exists in the dataset
     * @param failedLines   lines that threw (parse error, validation error, DB error)
     * @param errors        first {@value #MAX_ERRORS_REPORTED} error descriptions with line numbers
     */
    public record ImportResult(
            int totalLines,
            int importedCases,
            int skippedCases,
            int failedLines,
            List<String> errors
    ) {
    }
}
