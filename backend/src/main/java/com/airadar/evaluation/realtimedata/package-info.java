/**
 * Phase 17A real-data evaluation services.
 *
 * <p>Unlike the Phase 16A {@code evaluation.cluster} baseline (which replays a
 * frozen in-memory fixture through a strategy), this package reads whatever
 * real cluster state and scores are currently persisted in the database. This
 * is what makes the V1/V2 comparison meaningful on production-shaped data.
 *
 * <p>The package deliberately does not write to any clustering or scoring
 * tables: every method is read-only.
 */
package com.airadar.evaluation.realtimedata;
