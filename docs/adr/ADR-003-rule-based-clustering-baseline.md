# ADR-003: Start with a Rule-Based Clustering Baseline

## Status

Accepted

## Date

2026-07-02

## Context

AI Radar must group related normalized items into event-level `hot_cluster`
records. The project currently has no labeled clustering dataset and no measured
baseline for semantic edge cases.

## Problem

Choose the first clustering approach without making an opaque model the only
decision-maker or adding vector infrastructure before its value can be tested.

## Options

1. **Deterministic identifiers, hashes, and rules**
   - Explainable and inexpensive.
   - May miss paraphrases and cross-source semantic matches.
2. **Embedding similarity**
   - Can detect semantic similarity.
   - Requires model, threshold, vector storage, and evaluation decisions.
3. **LLM-only clustering**
   - Flexible for ambiguous language.
   - Costly, less deterministic, and unsuitable as the sole auditable decision.
4. **Hybrid rules and models immediately**
   - Potentially broad coverage.
   - Makes it difficult to establish which component improves the result.

## Decision

Start with deterministic source identifiers, normalized URLs, content hashes,
title normalization, keyword/entity rules, and recorded rule versions.

Store membership reason, method, optional match score, and active membership
history in `hot_cluster_item`. A `hot_item` can belong to at most one active
cluster while prior memberships remain traceable.

Do not use an LLM as the only clustering decision-maker.

## Rationale

A rule baseline establishes measurable behavior and produces understandable
error cases. Evaluation evidence can then justify whether embeddings or an LLM
improve specific failure classes.

## Consequences

- Early recall may be lower for semantic paraphrases.
- Rule versions and membership reasons must be stored.
- Re-clustering can retire an active membership and create a new one.
- An evaluation dataset is required before claiming clustering quality.

## Future Revisit Conditions

Evaluate embeddings or a hybrid approach after:

- labeled positive and negative item-pair or cluster samples exist;
- rule failures are categorized;
- precision, recall, and merge-error metrics can compare alternatives;
- the model and infrastructure cost can be measured.
