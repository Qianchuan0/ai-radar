package com.airadar.analysis.client.openai;

import com.airadar.analysis.client.AnalysisProviderException;
import com.airadar.analysis.client.ClusterEvidencePack;
import com.airadar.analysis.client.StructuredAnalysisModelClient;
import com.airadar.analysis.service.AnalysisProperties;
import com.airadar.analysis.vo.StructuredAnalysisResultVO;
import com.airadar.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * Real LLM provider for {@code StructuredAnalysisModelClient} backed by the
 * OpenAI Chat Completions API.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Chat Completions is preferred over the Responses API because it is the
 *       lowest-common-denominator OpenAI-compatible surface supported by both
 *       OpenAI itself and OpenAI-compatible gateways such as DeepSeek.</li>
 *   <li>The client uses {@code response_format=json_object} rather than the
 *       stricter {@code json_schema} mode. The stricter mode is not implemented
 *       by every compatible gateway, so we describe the JSON shape in the
 *       system prompt and parse the returned JSON ourselves.</li>
 * </ul>
 *
 * <p>Activation:
 * <ul>
 *   <li>Active when {@code ai-radar.analysis.provider=openai} (default).</li>
 *   <li>If the API key is missing, the bean still constructs so the application
 *       starts, but {@link #analyze(ClusterEvidencePack)} throws
 *       {@link AnalysisProviderException} with
 *       {@link ErrorCode#ANALYSIS_PROVIDER_NOT_CONFIGURED}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "ai-radar.analysis", name = "provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiStructuredAnalysisClient implements StructuredAnalysisModelClient {

    private final OpenAiAnalysisProperties properties;
    private final AnalysisProperties analysisProperties;
    private final OpenAiAnalysisPromptFactory promptFactory;
    private final OpenAiAnalysisResponseMapper responseMapper;
    private final ObjectMapper objectMapper;
    // Non-final so tests can inject a mock client via reflection.
    private OpenAIClient client;

    public OpenAiStructuredAnalysisClient(
            OpenAiAnalysisProperties properties,
            AnalysisProperties analysisProperties,
            OpenAiAnalysisPromptFactory promptFactory,
            OpenAiAnalysisResponseMapper responseMapper,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.analysisProperties = analysisProperties;
        this.promptFactory = promptFactory;
        this.responseMapper = responseMapper;
        this.objectMapper = objectMapper;
        this.client = properties.isConfigured() ? buildClient(properties) : null;
    }

    private static OpenAIClient buildClient(OpenAiAnalysisProperties props) {
        return OpenAIOkHttpClient.builder()
                .apiKey(props.apiKey())
                .baseUrl(props.baseUrl())
                .timeout(props.readTimeout())
                .maxRetries(Math.max(0, props.maxAttempts() - 1))
                .build();
    }

    @Override
    public StructuredAnalysisResultVO analyze(ClusterEvidencePack evidencePack) {
        if (!properties.isConfigured() || client == null) {
            throw new AnalysisProviderException(
                    ErrorCode.ANALYSIS_PROVIDER_NOT_CONFIGURED,
                    "OpenAI analysis provider is not configured. Set AI_RADAR_OPENAI_API_KEY to enable it."
            );
        }
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addSystemMessage(promptFactory.buildInstructions())
                    .addUserMessage(promptFactory.buildInput(evidencePack))
                    .model(ChatModel.of(analysisProperties.getModelName()))
                    .putAdditionalBodyProperty(
                            "response_format",
                            JsonValue.from(Map.of("type", "json_object"))
                    )
                    .build();
            ChatCompletion completion = client.chat().completions().create(params);
            String content = completion.choices().stream()
                    .flatMap(choice -> choice.message().content().stream())
                    .findFirst()
                    .orElseThrow(() -> new AnalysisProviderException(
                            ErrorCode.ANALYSIS_RESPONSE_PARSE_FAILED,
                            "OpenAI response did not contain message content."
                    ));
            OpenAiAnalysisOutput output = parseOutput(content);
            return responseMapper.map(output);
        } catch (AnalysisProviderException ex) {
            throw ex;
        } catch (OpenAIInvalidDataException ex) {
            throw new AnalysisProviderException(
                    ErrorCode.ANALYSIS_RESPONSE_PARSE_FAILED,
                    "OpenAI returned data that could not be parsed: " + safeMessage(ex),
                    ex
            );
        } catch (OpenAIIoException ex) {
            ErrorCode code = isTimeout(ex) ? ErrorCode.ANALYSIS_TIMEOUT : ErrorCode.ANALYSIS_UPSTREAM_ERROR;
            throw new AnalysisProviderException(code, "OpenAI I/O error: " + safeMessage(ex), ex);
        } catch (OpenAIServiceException ex) {
            throw new AnalysisProviderException(
                    ErrorCode.ANALYSIS_UPSTREAM_ERROR,
                    "OpenAI service error: " + safeMessage(ex),
                    ex
            );
        } catch (OpenAIException ex) {
            throw new AnalysisProviderException(
                    ErrorCode.ANALYSIS_UPSTREAM_ERROR,
                    "OpenAI SDK error: " + safeMessage(ex),
                    ex
            );
        } catch (RuntimeException ex) {
            throw new AnalysisProviderException(
                    ErrorCode.ANALYSIS_GENERATION_FAILED,
                    "OpenAI analysis generation failed: " + safeMessage(ex),
                    ex
            );
        }
    }

    private OpenAiAnalysisOutput parseOutput(String content) {
        try {
            return objectMapper.readValue(content, OpenAiAnalysisOutput.class);
        } catch (JsonProcessingException ex) {
            throw new AnalysisProviderException(
                    ErrorCode.ANALYSIS_RESPONSE_PARSE_FAILED,
                    "OpenAI response was not valid JSON for the structured output: " + ex.getOriginalMessage(),
                    ex
            );
        }
    }

    private boolean isTimeout(OpenAIIoException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("timeout");
    }

    private String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
