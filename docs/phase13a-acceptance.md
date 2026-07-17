# Phase 13A Acceptance: V1 Baseline Freeze

**Date:** 2026-07-15
**Status:** Complete

## Scope

Phase 13A closes the Phase 11/12 engineering baseline before Score V2 and clustering V2 work.

## Verified Capabilities

- Phase 11A, 11B, 12A, 12B-1, and 12B-2 acceptance scripts remain repeatable.
- V1 clustering behavior is frozen by `V1BaselineReplayIntegrationTest`.
- V1 scoring behavior is frozen by `V1BaselineReplayIntegrationTest`.
- `docs/v1-baseline-behavior.md` documents the V1 baseline and known limits.
- README, roadmap, and decision log describe the accepted baseline.

## V1 Replay Fixture

`backend/src/test/java/com/airadar/baseline/V1BaselineReplayIntegrationTest.java` creates a fixed two-item Hacker News replay:

- two hot items share the same source URL
- `RuleBasedClusterService` assigns one singleton membership and one `CANONICAL_URL` membership
- membership rows keep `hn-rule-v1`
- `RuleBasedScoringService` writes `hn-score-v1`
- score components are deterministic: points, comments, freshness, keyword, and cluster evidence

## Verification Steps

```powershell
.\scripts\accept-phase-13a.ps1
```

For a faster local check after the prior phase scripts have already been run:

```powershell
.\scripts\accept-phase-13a.ps1 -SkipPreviousPhaseScripts
```

## Latest Local Verification

Verified on 2026-07-15 with:

```powershell
.\scripts\accept-phase-13a.ps1 -SkipPreviousPhaseScripts
```

Result: passed.

- V1 baseline replay test: passed
- Frontend tests: 17 passed
- Frontend production build: passed
- Backend full regression after Phase 13A/13B changes: 168 passed, 0 failed

The full Phase 11A/11B/12A/12B-1/12B-2 scripts were also rerun individually on 2026-07-15 and passed.
