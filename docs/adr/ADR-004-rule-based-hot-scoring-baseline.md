# ADR-004: Start with Explainable Rule-Based Hot Scoring

## Status

Accepted

## Date

2026-07-02

## Context

AI Radar ranks event-level clusters using signals such as source weight,
interaction, freshness, multi-source occurrence, and keyword relevance. Users
must be able to understand why an event appears in the hot list.

## Problem

Choose an initial scoring method that can be replayed and evaluated before the
project has ranking labels or user behavior data.

## Options

1. **Rule and statistical signal scoring**
   - Deterministic, explainable, and replayable.
   - Requires manual weights and later calibration.
2. **LLM direct scoring**
   - Can interpret qualitative context.
   - Is less stable, more expensive, and difficult to reproduce.
3. **Learned ranking model**
   - Can optimize against behavior or labels.
   - No suitable training data currently exists.

## Decision

Use a versioned rule-based score for the first data flow. Store the total score,
calculation time, scoring version, and component breakdown for every calculation.
Score history is append-only.

An LLM must not be the sole source of the hot score.

## Rationale

The rule baseline supports explanation, replay, regression testing, and explicit
weight adjustment. It also creates the data needed to evaluate future ranking
approaches.

## Consequences

- Initial weights are hypotheses and must not be presented as validated quality.
- APIs and the future frontend should expose score components.
- Recalculation creates a new `hot_score` record instead of overwriting history.
- Ranking evaluation is needed before introducing learned or LLM-assisted scores.

## Future Revisit Conditions

Revisit the formula when:

- enough labeled Top-N examples or user feedback exists;
- component distributions are measured across sources;
- a candidate approach can be compared using stable ranking metrics;
- the replacement remains traceable to source evidence.
