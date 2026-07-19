package com.airadar.evaluation.service;

import com.airadar.common.exception.BusinessException;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.evaluation.service.verifier.ClusterPairPayloadValidator;
import com.airadar.evaluation.service.verifier.EvaluationPayloadValidator;
import com.airadar.evaluation.service.verifier.RankingRelevancePayloadValidator;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EvaluationSampleImportService}. Uses plain Mockito so
 * the suite runs without Spring Boot or Testcontainers. Verifies idempotent
 * skip, validation failure isolation, and that target/expected payloads land
 * with the correct shape. All JSONL test data is single-line per object per
 * the JSONL spec.
 */
class EvaluationSampleImportServiceTest {

    @TempDir
    Path tempDir;

    private EvaluationCaseMapper caseMapper;
    private EvaluationSampleImportService service;

    @BeforeEach
    void setUp() {
        caseMapper = mock(EvaluationCaseMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        List<EvaluationPayloadValidator> validators = List.of(
                new ClusterPairPayloadValidator(),
                new RankingRelevancePayloadValidator()
        );
        service = new EvaluationSampleImportService(caseMapper, objectMapper, validators);
    }

    @Test
    void importsClusterPairAnnotation() throws Exception {
        when(caseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        Path file = writeJsonl(tempDir.resolve("pairs.jsonl"), List.of(
                "{\"pairKey\":\"openai-gpt5-39241938-2507.12345\","
                        + "\"itemA\":{\"hotItemId\":12345,\"sourceType\":\"HACKER_NEWS\",\"externalId\":\"39241938\",\"title\":\"OpenAI launches GPT-5\"},"
                        + "\"itemB\":{\"hotItemId\":12347,\"sourceType\":\"ARXIV\",\"externalId\":\"2507.12345\",\"title\":\"GPT-5 Technical Report\"},"
                        + "\"expectation\":\"MUST_MERGE\",\"category\":\"MODEL_RELEASE\",\"annotator\":\"yi\"}"
        ));

        EvaluationSampleImportService.ImportResult result =
                service.importClusterPairs(7L, file);

        assertThat(result.totalLines()).isEqualTo(1);
        assertThat(result.importedCases()).isEqualTo(1);
        assertThat(result.skippedCases()).isZero();
        assertThat(result.failedLines()).isZero();

        ArgumentCaptor<EvaluationCaseEntity> captor =
                ArgumentCaptor.forClass(EvaluationCaseEntity.class);
        verify(caseMapper).insert(captor.capture());
        EvaluationCaseEntity entity = captor.getValue();
        assertThat(entity.getDatasetId()).isEqualTo(7L);
        assertThat(entity.getCaseCode()).isEqualTo("openai-gpt5-39241938-2507.12345");
        assertThat(entity.getCaseType()).isEqualTo(EvaluationCaseType.CLUSTER_PAIR_EXPECTATION);
        assertThat(entity.getTargetPayload().path("pairKey").asText())
                .isEqualTo("openai-gpt5-39241938-2507.12345");
        assertThat(entity.getExpectedPayload().path("expectation").asText()).isEqualTo("MUST_MERGE");
        assertThat(entity.getNotes()).contains("annotator=yi");
    }

    @Test
    void skipsClusterPairWhenCaseCodeAlreadyExists() throws Exception {
        when(caseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        Path file = writeJsonl(tempDir.resolve("pairs.jsonl"), List.of(
                "{\"pairKey\":\"duplicate-key\","
                        + "\"itemA\":{\"hotItemId\":1,\"sourceType\":\"HACKER_NEWS\",\"externalId\":\"a\",\"title\":\"title a\"},"
                        + "\"itemB\":{\"hotItemId\":2,\"sourceType\":\"ARXIV\",\"externalId\":\"b\",\"title\":\"title b\"},"
                        + "\"expectation\":\"MUST_NOT_MERGE\",\"category\":\"OTHER\"}"
        ));

        EvaluationSampleImportService.ImportResult result =
                service.importClusterPairs(1L, file);

        assertThat(result.importedCases()).isZero();
        assertThat(result.skippedCases()).isEqualTo(1);
        verify(caseMapper, never()).insert(any(EvaluationCaseEntity.class));
    }

    @Test
    void isolatesValidationFailureAcrossLines() throws Exception {
        when(caseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        Path file = writeJsonl(tempDir.resolve("pairs.jsonl"), List.of(
                // line 1: missing itemB (invalid)
                "{\"pairKey\":\"broken-1\","
                        + "\"itemA\":{\"hotItemId\":1,\"sourceType\":\"HACKER_NEWS\",\"externalId\":\"a\",\"title\":\"t\"},"
                        + "\"expectation\":\"MUST_MERGE\",\"category\":\"MODEL_RELEASE\"}",
                // line 2: valid
                "{\"pairKey\":\"ok-1\","
                        + "\"itemA\":{\"hotItemId\":1,\"sourceType\":\"HACKER_NEWS\",\"externalId\":\"a\",\"title\":\"t\"},"
                        + "\"itemB\":{\"hotItemId\":2,\"sourceType\":\"ARXIV\",\"externalId\":\"b\",\"title\":\"t2\"},"
                        + "\"expectation\":\"MUST_MERGE\",\"category\":\"MODEL_RELEASE\"}"
        ));

        EvaluationSampleImportService.ImportResult result =
                service.importClusterPairs(1L, file);

        assertThat(result.totalLines()).isEqualTo(2);
        assertThat(result.importedCases()).isEqualTo(1);
        assertThat(result.failedLines()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("line 1");
        verify(caseMapper, times(1)).insert(any(EvaluationCaseEntity.class));
    }

    @Test
    void importsRankingRelevanceAnnotation() throws Exception {
        when(caseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        Path file = writeJsonl(tempDir.resolve("ranking.jsonl"), List.of(
                "{\"windowStart\":\"2026-07-18T00:00:00Z\",\"windowEnd\":\"2026-07-18T23:59:59Z\","
                        + "\"clusterId\":8801,\"relevance\":\"HIGHLY_RELEVANT\",\"isMajorEvent\":true,"
                        + "\"annotator\":\"yi\"}"
        ));

        EvaluationSampleImportService.ImportResult result =
                service.importRankingRelevance(7L, file);

        assertThat(result.importedCases()).isEqualTo(1);
        assertThat(result.failedLines()).isZero();

        ArgumentCaptor<EvaluationCaseEntity> captor =
                ArgumentCaptor.forClass(EvaluationCaseEntity.class);
        verify(caseMapper).insert(captor.capture());
        EvaluationCaseEntity entity = captor.getValue();
        assertThat(entity.getCaseCode()).isEqualTo("2026-07-18T00:00:00Z|8801");
        assertThat(entity.getCaseType()).isEqualTo(EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION);
        assertThat(entity.getTargetPayload().path("clusterId").asLong()).isEqualTo(8801L);
        assertThat(entity.getExpectedPayload().path("relevance").asText()).isEqualTo("HIGHLY_RELEVANT");
        assertThat(entity.getExpectedPayload().path("isMajorEvent").asBoolean()).isTrue();
    }

    @Test
    void skipsBlankLines() throws Exception {
        when(caseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        Path file = Files.writeString(tempDir.resolve("pairs.jsonl"),
                "\n"
                        + "   \n"
                        + "{\"pairKey\":\"ok-blank\","
                        + "\"itemA\":{\"hotItemId\":1,\"sourceType\":\"HACKER_NEWS\",\"externalId\":\"a\",\"title\":\"t\"},"
                        + "\"itemB\":{\"hotItemId\":2,\"sourceType\":\"ARXIV\",\"externalId\":\"b\",\"title\":\"t2\"},"
                        + "\"expectation\":\"MUST_MERGE\",\"category\":\"MODEL_RELEASE\"}\n"
                        + "\n"
        );

        EvaluationSampleImportService.ImportResult result =
                service.importClusterPairs(1L, file);

        assertThat(result.totalLines()).isEqualTo(1);
        assertThat(result.importedCases()).isEqualTo(1);
    }

    @Test
    void missingFileThrowsBusinessException() {
        Path missing = tempDir.resolve("does-not-exist.jsonl");
        assertThatThrownBy(() -> service.importClusterPairs(1L, missing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does-not-exist.jsonl");
    }

    @Test
    void capsReportedErrorsAtFifty() throws Exception {
        when(caseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        // Build 60 broken lines (missing itemB)
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            lines.add("{\"pairKey\":\"broken-" + i + "\","
                    + "\"itemA\":{\"hotItemId\":1,\"sourceType\":\"HACKER_NEWS\",\"externalId\":\"a\",\"title\":\"t\"},"
                    + "\"expectation\":\"MUST_MERGE\",\"category\":\"MODEL_RELEASE\"}");
        }
        Path file = writeJsonl(tempDir.resolve("broken.jsonl"), lines);

        EvaluationSampleImportService.ImportResult result =
                service.importClusterPairs(1L, file);

        assertThat(result.failedLines()).isEqualTo(60);
        assertThat(result.errors()).hasSize(50);
    }

    private Path writeJsonl(Path target, List<String> jsonObjects) throws Exception {
        Files.writeString(target, String.join("\n", jsonObjects));
        return target;
    }
}
