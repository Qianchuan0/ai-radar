# ADR-006: Use OpenAI Chat Completions API with Official openai-java SDK for Real Structured Analysis

## Status

Accepted

## Date

2026-07-12

## Context

Phase 5 introduced `StructuredAnalysisModelClient` as the provider boundary and shipped `FakeStructuredAnalysisModelClient` as the only implementation so the analysis flow could be exercised end-to-end. Phase 10 must replace that fake with a real, replayable LLM provider while keeping the existing `cluster_analysis` persistence, the `/api/v1/hot-clusters/{clusterId}/analysis-runs` and `/api/v1/hot-clusters/{clusterId}/analysis` APIs, and the detail-page analysis card unchanged.

The provider replacement has to satisfy three additional constraints:

- The application must start even when the upstream API key is missing; in that case the analysis run must be persisted with a stable failure code instead of crashing startup.
- The existing `StructuredAnalysisResultVO` shape must keep working as the analysis contract.
- Tests must not require live LLM calls, but a manual live verification path should exist.

## Problem

Choose a real provider path for structured cluster analysis that satisfies the constraints above without over-engineering the analysis module or pulling in heavyweight infrastructure, while staying compatible with both OpenAI itself and OpenAI-compatible gateways (e.g. DeepSeek) that the team may use for local verification.

## Options

1. OpenAI Chat Completions API + Structured Outputs via `responseFormat(Class)`, using the official `openai-java` SDK
2. OpenAI Responses API + Structured Outputs via `text(Class)` on the same SDK
3. Hand-written HTTP client against Chat Completions, following the existing `HuggingFaceClient` style
4. A higher-level framework such as Spring AI or LangChain4j

## Decision

Use the OpenAI Chat Completions API through the official `com.openai:openai-java` SDK, with `response_format=json_object` plus an in-prompt schema description.

- New dependency: `com.openai:openai-java` (Maven Central).
- The provider client lives at `analysis/client/openai/OpenAiStructuredAnalysisClient` and implements the existing `StructuredAnalysisModelClient` boundary.
- The request sets `response_format={type:json_object}` via the SDK's `putAdditionalBodyProperty`, and the system message documents the exact JSON shape expected. The model's JSON response is parsed by `ObjectMapper` into the `OpenAiAnalysisOutput` POJO, then mapped into the existing `StructuredAnalysisResultVO`.
- The stricter `response_format={type:json_schema}` mode (which the SDK exposes via `responseFormat(Class)`) was tried first but is rejected by OpenAI-compatible gateways such as DeepSeek ("This response_format type is unavailable now"), so the implementation falls back to `json_object` to keep a single code path across providers.
- The system message is built by `OpenAiAnalysisPromptFactory.buildInstructions()`; the user message is built by `OpenAiAnalysisPromptFactory.buildInput(ClusterEvidencePack)`.
- Provider selection is driven by `ai-radar.analysis.provider`. The default is `openai`; `fake` remains available for tests and explicit fallback.
- When the API key is missing, the bean still constructs and the application starts; calling `analyze(...)` throws `AnalysisProviderException` with `ErrorCode.ANALYSIS_PROVIDER_NOT_CONFIGURED`, which `AnalysisService` persists into `cluster_analysis.failureCode`.

## Rationale

- Chat Completions is the lowest-common-denominator OpenAI-compatible surface. It is supported by OpenAI itself and by OpenAI-compatible gateways such as DeepSeek (which the team uses for live verification). The Responses API is not yet implemented by these gateways, so choosing Chat Completions keeps the same code path usable against multiple backends.
- The official SDK is stable on Maven Central and drives Structured Outputs through `responseFormat(Class)` on this path as well, including local JSON Schema validation and response deserialization. Hand-writing the same surface would be brittle and re-implement SDK responsibilities.
- Structured Outputs lets the existing VO shape drive the schema, so the analysis contract stays the same and the database migration footprint stays at zero.
- The SDK's exception hierarchy (`OpenAIServiceException`, `OpenAIIoException`, `OpenAIInvalidDataException`, `OpenAIException`) maps cleanly onto the Phase 10 failure codes (`ANALYSIS_UPSTREAM_ERROR`, `ANALYSIS_TIMEOUT`, `ANALYSIS_RESPONSE_PARSE_FAILED`, `ANALYSIS_SCHEMA_INVALID`, `ANALYSIS_GENERATION_FAILED`), which makes persisted failures useful for later evaluation and reports.
- Keeping `provider=fake` behind `@ConditionalOnProperty` preserves the existing analysis integration tests without forcing them to mock the new SDK.
- Deferring heavier frameworks (Spring AI, LangChain4j) avoids introducing abstraction layers, retry policies, and configuration surfaces that the current single-provider scope does not yet justify.

## Consequences

- The backend gains one runtime dependency (`openai-java`) and its transitive OkHttp/Jackson dependencies. The team must keep an eye on SDK major versions during upgrades.
- Live calls cost tokens and require an API key. Default tests stay offline; a separate optional live verification script handles real calls.
- Because the wire format is OpenAI-compatible Chat Completions with `response_format=json_object`, swapping backends between OpenAI and compatible gateways is a `base-url`/`api-key`/`model-name` change, not a code change.
- Schema enforcement is weaker than with `response_format=json_schema`: the model is told the shape via the system prompt and is asked to emit a JSON object, but the SDK does not validate the response against a strict JSON Schema at the wire level. `OpenAiAnalysisResponseMapper` catches missing fields, malformed evidence refs, and illegal confidence values; violations are persisted as `ANALYSIS_RESPONSE_PARSE_FAILED`.
- The `response_format=json_schema` path is not blocked by this decision; if a future target only needs to support OpenAI itself, a `json_schema` variant can be added behind a configuration flag.
- The Responses API path is similarly not blocked; a future revision can add it as an alternative implementation if a Responses-only feature becomes important.

## Future Revisit Conditions

- When a second real provider is needed and selection logic becomes non-trivial
- When prompt iteration frequency justifies prompt-asset tooling or evaluation harnesses beyond the Phase 8 baseline
- When structured-output drift forces us to maintain schema versions separately from VO definitions
- When Responses API-only features (e.g. hosted tools, native function-calling improvements) become load-bearing

