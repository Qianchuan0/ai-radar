# ADR-005: Use Vue 3 for the Frontend MVP

## Status

Accepted

## Date

2026-07-03

## Context

Phase 2 has already produced a real backend closed loop with Hacker News data, event-level clusters, score breakdowns, and evidence APIs. Phase 3 needs a frontend MVP that can consume those real endpoints and prove the product loop from crawl to visible hot clusters.

The frontend scope is intentionally narrow:

- hot cluster list
- detail page
- score breakdown
- source evidence
- loading, empty, and error states

The team does not need charts, global workflow orchestration, or complex client-side state yet.

## Problem

Choose a frontend stack that can deliver the MVP quickly, stay aligned with the current backend contract, and avoid introducing unnecessary complexity before the product workflow is proven.

## Options

1. Vue 3 + TypeScript + Vite + Vue Router + Ant Design Vue + Axios
2. React + TypeScript + Vite + React Router + UI library + Axios
3. Vue 3 + extra infrastructure now, such as Pinia and ECharts

## Decision

Use Vue 3, TypeScript, Vite, Vue Router, Ant Design Vue, and Axios for the Phase 3 frontend MVP.

Do not introduce Pinia, ECharts, or other heavier frontend infrastructure in this phase.

## Rationale

- Vue 3 is sufficient for a list-detail workflow with moderate interaction complexity.
- Vite provides a fast local development loop and simple proxy configuration for the backend API.
- Vue Router is enough for query-driven list filters and detail navigation.
- Ant Design Vue gives usable, consistent UI primitives without spending this phase on custom component infrastructure.
- Axios is enough for a thin API layer and uniform error extraction.
- URL query parameters can carry the list state, so a global store is unnecessary at this stage.
- The backend currently exposes list/detail APIs, not aggregation APIs, so charts would either be fake or premature.

## Consequences

- The frontend can ship quickly and stay tightly coupled to the current backend reality.
- Bundle size should be watched because importing a UI library can increase the initial payload.
- If future phases add cross-page state, richer interactions, or analytics dashboards, the frontend architecture may need to expand.

## Future Revisit Conditions

- When multiple pages need shared client-side state beyond route query parameters
- When the backend exposes aggregate analytics endpoints that justify charting
- When the design system or component customization needs exceed what the chosen UI layer provides
