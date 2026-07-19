# ADR-008: Event Cluster V2 with Deterministic Layered Matching

## Status

Accepted

## Date

2026-07-17

## Context

Phase 2 shipped a rule-based clustering baseline (ADR-003) that merges items
only when they share a canonical URL. The Phase 16A baseline fixture
(`ClusterBaselineFixtures.defaultFixture()`) makes the resulting gap
measurable: V1 merges 1 of 5 must-merge groups (recall 0.20) because only
one group shares a URL; the other four describe the same event via different
URLs, sources, and phrasings.

Phase 13B introduced `SourceRole` and `NormalizedSignal`, Phase 14 added
24h growth tracking, and Phase 15 added the shadow scoring pattern. Those
layers give us a safe place to land a V2 clustering pipeline without
disturbing the V1 baseline.

## Problem

Upgrade URL-level merging to event-level clustering so the same event
reported by different URLs, sources, and phrasings can land in the same
`hot_cluster` — without:

- wrongly merging same-entity-different-action events
  (release vs pricing vs security disclosure),
- turning an LLM or an embedding into the decision maker,
- introducing `pgvector` or other vector infrastructure before its value
  is measured,
- breaking the V1 baseline that downstream code (scoring, analysis,
  alerts, reports) depends on.

## Options

1. **Drop V1 and replace with V2**
   - Simplest code path long-term.
   - Loses the V1 baseline and the ability to A/B compare on the same
     fixture. Any V2 regression ships directly to production ranking.

2. **Add V2 as a parallel online strategy behind a feature flag**
   - Either strategy can be flipped without code change.
   - Both strategies would write `hot_cluster_item`, but the table's unique
     partial index (`(hot_cluster_id) WHERE removed_at IS NULL AND
     is_primary`, plus `(hot_item_id) WHERE removed_at IS NULL`) forbids
     two strategies from concurrently owning the same item. Coordinating
     ownership adds governance complexity that belongs in a later phase.

3. **V1 online + V2 shadow (Phase 15 pattern) — selected**
   - V1 keeps controlling online `hot_cluster_item` rows; downstream code
     sees no behavior change.
   - V2 runs evaluate-only, persisting every considered candidate (accept,
     reject, review-required, no-candidate) to a separate
     `cluster_match_decision` table.
   - The same `ClusterAssignmentStrategy` interface can be exercised
     directly by `ClusterEvaluationService` for offline comparison.

## Decision

Ship V2 as a deterministic, layered pipeline that runs in shadow alongside
V1:

```text
hot_item
  -> ItemFeatureExtractor (title, external ids, entities, keywords,
                           event time, publisher, event type)
  -> ClusterCandidateRetriever (bounded to 72h / 50 candidates,
                                5 retrieval signals)
  -> LayeredMatcher (L1 identifiers, L2 entity + org + event type + time,
                     L3 weighted similarity)
  -> cluster_match_decision (ACCEPTED / REJECTED / REVIEW_REQUIRED /
                             NO_CANDIDATE with match_reason)
```

V2 entity resolution relies on a curated `EntityAliasDictionary` plus
regex-based external id extraction. The first version does not use pgvector,
embeddings, or any LLM in the decision path. Candidate retrieval is bounded
(default 72h, max 50 candidates) so it never scans the full feature table.

Layered match thresholds follow the Phase 16 plan exactly:

- L1 (canonical URL, arXiv id, GitHub repo, HF model id) — auto-accept at
  0.95-1.00.
- L2 (shared product + shared org + compatible event type + event time
  within 48h) — auto-accept at 0.85.
- L3 weighted similarity = `titleSim * 0.30 + entityOverlap * 0.35 +
  keywordOverlap * 0.15 + actionConsistency * 0.15 + timeProximity * 0.05`.
  `>= 0.82` ACCEPTED, `< 0.60` REJECTED, in between REVIEW_REQUIRED.

The pipeline is wired through `ClusterAssignmentOrchestrator`, which mirrors
`ScoringOrchestrator`: V1 (`CanonicalUrlClusterStrategy`, wrapping the
unchanged `RuleBasedClusterService`) is authoritative;
`ai-radar.cluster.shadow-strategy=event-rule-v2` enables V2 evaluate-only.

When V2 eventually becomes the online strategy (Phase 17), the same code
path (`EventRuleClusterStrategy.assign`) is exercised through the same
interface, so the migration is incremental and reversible.

## Rationale

- Phase 16A fixture gives a measurable target: V1 recall is 0.20, and V2
  targets >= 0.80 without sacrificing precision. Every change to the V2
  pipeline can be re-evaluated on the same inputs.
- The Phase 15 shadow pattern is already proven for scoring; clustering
  reuses the same shape so operators reason about one mental model.
- `cluster_match_decision` keeps a full audit trail (every candidate, every
  outcome, every match reason) without polluting `hot_cluster_item`.
  Future Phase 17 governance can resolve REVIEW_REQUIRED rows without
  rescanning the corpus.
- A bounded candidate retriever avoids the cost and complexity of vector
  indexing for V1 of V2. Vector retrieval can be evaluated later against
  the deterministic baseline.

## Consequences

- V2 shadow writes only `cluster_match_decision`; online cluster state is
  unchanged while V2 is in shadow.
- V2 is wired through `ClusterAssignmentOrchestrator`, which
  `ItemPipelineService` now calls instead of `RuleBasedClusterService`
  directly. V1 behavior is preserved because the orchestrator always
  delegates online assignment to `CanonicalUrlClusterStrategy`.
- `hot_item_feature` becomes a persistent, audited feature store. The
  schema must evolve carefully because V2 matchers depend on it.
- Cluster merge/split governance (resolving REVIEW_REQUIRED rows,
  splitting mis-merged clusters) is explicitly deferred to a later phase.
- Setting `ai-radar.cluster.strategy=event-rule-v2` is reserved for Phase
  17 — making V2 the online strategy requires governance work first.

## Future Revisit Conditions

- Promote V2 to online strategy after REVIEW_REQUIRED rows can be
  resolved operationally and false-merge / false-split metrics stay
  within agreed SLO.
- Revisit pgvector / embedding retrieval if the bounded candidate
  retriever consistently misses obvious matches on real production data.
- Revisit LLM-assisted entity resolution once the deterministic
  dictionary's coverage becomes the bottleneck (measured by
  feature-extraction coverage metrics, not by intuition).

## Phase 17C Gradual Online Adoption

Phase 17C lifts V2 out of evaluate-only shadow into a gated, staged
online writer. The architectural commitment is unchanged — V1
(`hn-rule-v1`) stays authoritative, and `ai-radar.cluster.strategy`
remains pinned to `hn-rule-v1`. The new path is additive:

- V1 always creates the initial singleton cluster for every crawled
  item, exactly as before.
- When `ai-radar.cluster.v2-online.enabled=true` (default `false`)
  plus a non-zero `traffic-percent` plus an explicit
  `allowed-match-levels` set, `ClusterAssignmentOrchestrator` dispatches
  to `V2OnlineAssignmentService` for items that clear the deterministic
  id-hash traffic gate and (optional) source allowlist.
- `V2OnlineAssignmentService` runs `EventRuleClusterStrategy.evaluateForOnline`
  (full V2 pipeline, picks best candidate, persists every decision,
  excludes self-match), applies the level + L3-min-score gate, and —
  only when ACCEPTED clears every gate — calls `MoveItemService.move`
  with `OperatorType.SYSTEM` to relocate the item from its V1 singleton
  into the V2 target cluster. Every successful relocation writes a
  `cluster_membership_history` row through the same governance path
  manual ops use.
- V2 online runs inside a `Propagation.NESTED` savepoint so any V2-side
  failure rolls back only the V2 work. V1's singleton is intact and the
  crawl loop continues.
- REVIEW_REQUIRED decisions never write membership. The V2 online path
  proactively calls `ClusterReviewService.materializeOpenTasks()` so
  grey-zone matches surface in the review queue without waiting for a
  poll.
- `GET /api/v1/cluster-strategy/status` returns the live configuration
  and a derived rollout stage (`V1_ONLY`, `SHADOW_ONLY`, `STAGE_2_L1`,
  `STAGE_3_L2`, `STAGE_4_L3`) so operators can confirm which path the
  crawl pipeline is taking.

Rollback is one property flip: set `AI_RADAR_CLUSTER_V2_ONLINE_ENABLED=false`
(or drop `traffic-percent` to 0 and re-validate) and the orchestrator
falls back to the V1-only / shadow-only path that has been in production
since Phase 16.
