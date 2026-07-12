# Phase 9A Acceptance

## Scope

This document closes `Phase 9A` in `ai-radar`:

- Hugging Face Models as the fourth heterogeneous source
- source connector implementation template for future source expansion

## Acceptance Command

Run the repo-level acceptance script:

```powershell
.\scripts\accept-phase-9a.ps1
```

## What The Script Verifies

1. `backend\mvnw.cmd -Dtest=HuggingFaceClientTest,HuggingFaceRawDataFlowIntegrationTest test`
   - verifies Hugging Face client request/response parsing
   - verifies invalid upstream payload handling
   - verifies the closed loop `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster`
   - verifies `HUGGING_FACE` records persist with normalized metrics and source URLs
2. `frontend\npm test -- --run`
   - verifies frontend helpers remain green after `HUGGING_FACE` enum and route-shell updates
3. `frontend\npm run build`
   - verifies the production bundle still compiles with Hugging Face source labels and filters

## Acceptance Evidence

On the verification run completed on `2026-07-12`, the following checks passed:

- backend targeted tests: `BUILD SUCCESS`
- backend targeted tests: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- frontend tests: `3` files passed, `11` tests passed
- frontend production build: passed

## Completion Judgment

Phase 9A is accepted because the repository now has:

- Hugging Face client, collector, config validation, typed properties, and hot-item normalizer
- integration coverage proving `HUGGING_FACE` reaches persisted `raw_item`, `hot_item`, and `hot_cluster`
- frontend-compatible source enum and labels so Hugging Face records can flow through existing list/detail/report/alert views
- a reusable source connector checklist in [docs/source-connector-template.md](source-connector-template.md)

## Manual Recheck

If a maintainer wants to re-run the same acceptance later, use:

```powershell
.\scripts\accept-phase-9a.ps1
```
