package com.airadar.analysis.client.openai;

import com.airadar.analysis.client.AnalysisProviderException;
import com.airadar.analysis.client.ClusterEvidencePack;
import com.airadar.analysis.service.AnalysisProperties;
import com.airadar.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiStructuredAnalysisClientTest {

    private OpenAiAnalysisPromptFactory promptFactory;
    private OpenAiAnalysisResponseMapper responseMapper;
    private AnalysisProperties analysisProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        promptFactory = new OpenAiAnalysisPromptFactory();
        responseMapper = new OpenAiAnalysisResponseMapper();
        analysisProperties = new AnalysisProperties();
        analysisProperties.setModelName("gpt-4.1-mini");
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldFailWithProviderNotConfiguredWhenApiKeyMissing() {
        OpenAiAnalysisProperties props = configured("");

        OpenAiStructuredAnalysisClient client = new OpenAiStructuredAnalysisClient(
                props, analysisProperties, promptFactory, responseMapper, objectMapper
        );

        assertThatThrownBy(() -> client.analyze(evidencePack()))
                .isInstanceOf(AnalysisProviderException.class)
                .extracting(ex -> ((AnalysisProviderException) ex).errorCode())
                .isEqualTo(ErrorCode.ANALYSIS_PROVIDER_NOT_CONFIGURED);
    }

    @Test
    void shouldMapIoTimeoutToAnalysisTimeout() {
        OpenAiStructuredAnalysisClient client = clientWithFailingResponses(
                new OpenAIIoException("Request timed out", new java.net.SocketTimeoutException("read timed out"))
        );

        assertThatThrownBy(() -> client.analyze(evidencePack()))
                .isInstanceOf(AnalysisProviderException.class)
                .extracting(ex -> ((AnalysisProviderException) ex).errorCode())
                .isEqualTo(ErrorCode.ANALYSIS_TIMEOUT);
    }

    @Test
    void shouldMapNonTimeoutIoErrorToUpstreamError() {
        OpenAiStructuredAnalysisClient client = clientWithFailingResponses(
                new OpenAIIoException("connection reset")
        );

        assertThatThrownBy(() -> client.analyze(evidencePack()))
                .isInstanceOf(AnalysisProviderException.class)
                .extracting(ex -> ((AnalysisProviderException) ex).errorCode())
                .isEqualTo(ErrorCode.ANALYSIS_UPSTREAM_ERROR);
    }

    @Test
    void shouldMapInvalidDataExceptionToResponseParseFailed() {
        OpenAiStructuredAnalysisClient client = clientWithFailingResponses(
                new OpenAIInvalidDataException("missing field headline")
        );

        assertThatThrownBy(() -> client.analyze(evidencePack()))
                .isInstanceOf(AnalysisProviderException.class)
                .extracting(ex -> ((AnalysisProviderException) ex).errorCode())
                .isEqualTo(ErrorCode.ANALYSIS_RESPONSE_PARSE_FAILED);
    }

    @Test
    void shouldMapGenericRuntimeExceptionToGenerationFailed() {
        OpenAiStructuredAnalysisClient client = clientWithFailingResponses(
                new IllegalStateException("unexpected SDK state")
        );

        assertThatThrownBy(() -> client.analyze(evidencePack()))
                .isInstanceOf(AnalysisProviderException.class)
                .extracting(ex -> ((AnalysisProviderException) ex).errorCode())
                .isEqualTo(ErrorCode.ANALYSIS_GENERATION_FAILED);
    }

    @Test
    void shouldMapOpenAiBaseExceptionToUpstreamError() {
        OpenAiStructuredAnalysisClient client = clientWithFailingResponses(
                new OpenAIException("generic sdk failure")
        );

        assertThatThrownBy(() -> client.analyze(evidencePack()))
                .isInstanceOf(AnalysisProviderException.class)
                .extracting(ex -> ((AnalysisProviderException) ex).errorCode())
                .isEqualTo(ErrorCode.ANALYSIS_UPSTREAM_ERROR);
    }

    private OpenAiStructuredAnalysisClient clientWithFailingResponses(RuntimeException failure) {
        OpenAIClient mockClient = mock(OpenAIClient.class);
        when(mockClient.chat()).thenThrow(failure);
        return clientWithMockClient(mockClient);
    }

    private OpenAiStructuredAnalysisClient clientWithMockClient(OpenAIClient mockClient) {
        OpenAiStructuredAnalysisClient client = new OpenAiStructuredAnalysisClient(
                configured("test-key"),
                analysisProperties,
                promptFactory,
                responseMapper,
                objectMapper
        );
        ReflectionTestUtils.setField(client, "client", mockClient);
        return client;
    }

    private OpenAiAnalysisProperties configured(String apiKey) {
        return new OpenAiAnalysisProperties(
                "https://api.openai.com/v1",
                apiKey,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                1
        );
    }

    private ClusterEvidencePack evidencePack() {
        return new ClusterEvidencePack(
                1L,
                "Cluster title",
                "Cluster summary",
                null,
                List.of(com.airadar.source.model.SourceType.HACKER_NEWS),
                List.of(new ClusterEvidencePack.EvidenceItemSnapshot(
                        10L,
                        com.airadar.source.model.SourceType.HACKER_NEWS,
                        "Evidence title",
                        "Evidence summary",
                        "https://example.com/10",
                        "alice",
                        null
                )),
                null,
                null
        );
    }
}
