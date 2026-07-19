package com.airadar.cluster.feature.extractor;

/**
 * Coarse event-type classification used by the V2 layered match rules.
 *
 * <p>Phase 16 V2 only uses this to keep same-entity-different-action events
 * apart (e.g. RELEASE vs SECURITY_INCIDENT on the same product). The values
 * are intentionally coarse: finer-grained taxonomy belongs in a later phase.
 *
 * <p>{@link #UNKNOWN} is the fallback when no signal matches; the layered
 * matcher treats UNKNOWN conservatively (it does not by itself block an
 * ACCEPT).
 */
public enum EventType {

    RELEASE,
    UPDATE,
    PRICING,
    FUNDING,
    ACQUISITION,
    RESEARCH,
    SECURITY_INCIDENT,
    BENCHMARK,
    OPEN_SOURCE,
    POLICY,
    UNKNOWN
}
