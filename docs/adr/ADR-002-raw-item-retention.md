# ADR-002: Retain Immutable Raw Item Snapshots

## Status

Accepted

## Date

2026-07-02

## Context

AI Radar converts external source records into normalized items, event clusters,
and scores. Processing rules will evolve, and clustering or scoring mistakes
must be traceable to the evidence that entered the system.

## Problem

Decide whether normalized results can replace source records or whether the
original source representation must be retained.

## Options

1. **Store only normalized `hot_item` records**
   - Uses less storage.
   - Loses source evidence and prevents deterministic reprocessing.
2. **Keep one mutable latest raw record per external item**
   - Retains current source data.
   - Overwrites history and makes task-level reproduction unreliable.
3. **Keep immutable raw snapshots per crawl task**
   - Preserves evidence and collection history.
   - Requires retention monitoring and additional storage.

## Decision

Retain `raw_item` as an immutable snapshot associated with the crawl task that
observed it. A task cannot store the same `(source_type, external_id)` twice.
Repeated crawls may store later snapshots of the same external item.

`hot_item` represents the current normalized logical item and references its
latest raw snapshot. Source type and external ID keep older snapshots traceable.

## Rationale

Immutable snapshots support source tracing, transformation replay, incident
diagnosis, and evaluation dataset construction. These capabilities are core to
an intelligence pipeline whose output must be explainable.

## Consequences

- Raw storage grows as crawls repeat.
- Raw payloads are not used directly as public API models.
- Retention and archival policies will be needed after real volume is measured.
- Tokens, credentials, and authentication headers must never be stored as raw
  item data.

## Future Revisit Conditions

Revisit storage retention when real source volume and cost are known. Any
retention policy must preserve enough evidence for replay and evaluation and
must not silently turn `raw_item` into a mutable latest-value table.
