/**
 * Phase 16A minimal clustering evaluation baseline.
 *
 * <p>This package contains the in-memory fixture model
 * ({@link com.airadar.evaluation.cluster.ClusterBaselineFixture}), the default
 * frozen fixture ({@link com.airadar.evaluation.cluster.ClusterBaselineFixtures}),
 * the replay-and-score service
 * ({@link com.airadar.evaluation.cluster.ClusterEvaluationService}), and the
 * report/case models emitted back to callers.
 *
 * <p>The runner is strategy-agnostic: it accepts any
 * {@link com.airadar.cluster.strategy.ClusterAssignmentStrategy} so the same
 * fixture exercises both {@code hn-rule-v1} and {@code event-rule-v2} for
 * side-by-side comparison.
 *
 * <p>Replay contract: every {@code evaluate} call truncates the
 * clustering-related tables first so runs are deterministic across CI and
 * local environments. The service is intentionally not exposed through any
 * controller.
 */
package com.airadar.evaluation.cluster;
