# Phase 13B Acceptance: Source Roles and Normalized Signals

**Date:** 2026-07-15
**Status:** Complete

## Scope

Phase 13B introduces the minimal signal layer that separates source semantics from V1 compatibility metrics.

## Verified Capabilities

- `SourceRole` defines source semantics.
- `NormalizedSignal` preserves raw metrics and separates attention, discussion, adoption, authority, relevance, and rank.
- `SourceSignalAdapter` implementations exist for:
  - `HACKER_NEWS`
  - `GITHUB`
  - `HUGGING_FACE`
  - `BING_SEARCH`
  - `DUCKDUCKGO_SEARCH`
- `SourceSignalAdapterRegistry` detects duplicate adapters and missing adapters.
- Search sources contribute relevance/rank but zero social heat.
- `SearchSignalAdapter` handles `totalCount <= 0` without infinite relevance.
- V1 scoring remains unchanged and is guarded by the Phase 13A replay test.

## Verification Steps

```powershell
.\scripts\accept-phase-13b.ps1
```

## Latest Local Verification

Verified on 2026-07-15 with:

```powershell
.\scripts\accept-phase-13b.ps1
```

Result: passed.

- Signal adapter/model/registry tests: 11 passed
- V1 baseline replay test: passed
- Backend full regression after Phase 13A/13B changes: 168 passed, 0 failed
