# Phase 6-8 Acceptance

## Scope

This document closes the remaining unaccepted phases in `ai-radar`:

- Phase 6: Subscription and Alerts
- Phase 7: Daily Report
- Phase 8: Evaluation

## Acceptance Command

Run the repo-level acceptance script:

```powershell
.\scripts\accept-phases-6-8.ps1
```

## What The Script Verifies

1. `backend\mvnw.cmd test`
   - includes alert, daily report, and evaluation integration coverage
   - verifies Flyway migrations through `V6__add_evaluation.sql`
   - verifies the backend API paths used by Phase 6-8
2. `frontend\npm test`
   - verifies the frontend test suite stays green
3. `frontend\npm run build`
   - verifies `/alerts`, `/reports/daily`, and `/evaluation` pages compile into the production bundle

## Acceptance Evidence

On the verification run completed on `2026-07-10`, the following checks passed:

- backend test suite: `BUILD SUCCESS`
- backend tests: `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`
- frontend tests: `1` file passed, `3` tests passed
- frontend production build: passed

## Completion Judgment

Phase 6 is accepted because the repository now has:

- persisted `subscription_rule` and `alert_record` flow
- manual alert matching API
- alert review page in the frontend

Phase 7 is accepted because the repository now has:

- persisted `daily_report` model
- manual report generation API
- daily report list/detail frontend page

Phase 8 is accepted because the repository now has:

- labeled evaluation dataset and case APIs
- manual evaluation run API with persisted metrics and error analysis
- frontend evaluation page for dataset selection, metrics, and failed-case inspection

## Manual Recheck

If a maintainer wants to re-run the same acceptance later, use:

```powershell
.\scripts\accept-phases-6-8.ps1
```
