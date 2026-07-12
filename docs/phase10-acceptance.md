# Phase 10 Acceptance

## Scope

This document closes `Phase 10` in `ai-radar`:

- replace the Phase 5 fake structured analysis provider with a real OpenAI-compatible provider
- keep the existing `cluster_analysis` persistence, API shape, and detail-page analysis card intact
- preserve application startup when the API key is missing and persist a stable failure code instead

## Acceptance Command

Run the repo-level acceptance script:

```powershell
.\scripts\accept-phase-10.ps1
```

## What The Script Verifies

1. `backend\mvnw.cmd -Dtest=OpenAiAnalysisPromptFactoryTest,OpenAiAnalysisResponseMapperTest,OpenAiStructuredAnalysisClientTest,OpenAiAnalysisProviderIntegrationTest,ClusterAnalysisIntegrationTest test`
   - verifies prompt construction, response mapping, and OpenAI client error handling
   - verifies the default `provider=openai` path without live OpenAI calls
   - verifies both success persistence and `ANALYSIS_PROVIDER_NOT_CONFIGURED` failure persistence
2. `backend\mvnw.cmd test`
   - verifies Phase 10 does not regress alert, report, evaluation, and prior analysis flows
3. `frontend\npm test -- --run`
   - verifies frontend helper and layout tests remain green
4. `frontend\npm run build`
   - verifies the detail page and surrounding frontend still compile with Phase 10 changes

## Acceptance Evidence

On the verification run completed on `2026-07-12`, the following checks passed:

- backend Phase 10 targeted tests: passed
- backend full regression tests: passed
- frontend tests: passed
- frontend production build: passed

## Completion Judgment

Phase 10 is accepted because the repository now has:

- a real `openai` analysis provider as the default runtime path
- `fake` retained only as an explicit fallback for tests and local override
- persisted failure codes for provider-not-configured, timeout, upstream, parse, schema, and generation failure classes
- provider metadata exposed through the existing analysis APIs and detail-page analysis card
- a repeatable acceptance script plus optional live verification path

## Manual Recheck

If a maintainer wants to re-run the same acceptance later, use:

```powershell
.\scripts\accept-phase-10.ps1
```

For optional live verification against a reachable OpenAI-compatible endpoint:

```powershell
powershell -File .\scripts\live-verify-openai.ps1 -ClusterId <id>
```
