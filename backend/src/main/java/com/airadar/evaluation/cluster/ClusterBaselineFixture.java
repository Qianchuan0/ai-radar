package com.airadar.evaluation.cluster;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Frozen in-memory baseline used by {@link ClusterEvaluationService} to
 * evaluate a {@code ClusterAssignmentStrategy}.
 *
 * <p>A fixture has three parts:
 * <ul>
 *   <li>{@link #getItems()} — the input items, in the order they should be
 *       assigned</li>
 *   <li>{@link #getMustMergeGroups()} — groups of item keys that must all end
 *       up in the same cluster (a "must-merge" violation is a false split)</li>
 *   <li>{@link #getMustNotMergePairs()} — pairs of item keys that must end up
 *       in different clusters (a "must-not-merge" violation is a false merge)</li>
 * </ul>
 *
 * <p>Fixtures are versioned so reports can cite the exact fixture version they
 * were produced against. Once a fixture is published (referenced by an
 * acceptance script or report), it must not be mutated — instead, ship a new
 * fixture with a new version tag so historical reports stay interpretable.
 */
public final class ClusterBaselineFixture {

    private final String version;
    private final String description;
    private final List<FixtureInputItem> items;
    private final List<Set<String>> mustMergeGroups;
    private final List<MustNotMergePair> mustNotMergePairs;

    public ClusterBaselineFixture(
            String version,
            String description,
            List<FixtureInputItem> items,
            List<Set<String>> mustMergeGroups,
            List<MustNotMergePair> mustNotMergePairs
    ) {
        this.version = Objects.requireNonNull(version, "version");
        this.description = Objects.requireNonNull(description, "description");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.mustMergeGroups = List.copyOf(Objects.requireNonNull(mustMergeGroups, "mustMergeGroups"));
        this.mustNotMergePairs = List.copyOf(Objects.requireNonNull(mustNotMergePairs, "mustNotMergePairs"));
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public List<FixtureInputItem> getItems() {
        return items;
    }

    public List<Set<String>> getMustMergeGroups() {
        return mustMergeGroups;
    }

    public List<MustNotMergePair> getMustNotMergePairs() {
        return mustNotMergePairs;
    }

    /**
     * Ordered pair of item keys that must NOT land in the same cluster.
     *
     * <p>{@link ClusterEvaluationService} treats the pair as unordered when
     * comparing clusters: either ordering counts as the same expectation.
     */
    public static final class MustNotMergePair {

        private final String keyA;
        private final String keyB;

        public MustNotMergePair(String keyA, String keyB) {
            this.keyA = Objects.requireNonNull(keyA, "keyA");
            this.keyB = Objects.requireNonNull(keyB, "keyB");
            if (keyA.equals(keyB)) {
                throw new IllegalArgumentException("MustNotMergePair keys must differ: " + keyA);
            }
        }

        public String getKeyA() {
            return keyA;
        }

        public String getKeyB() {
            return keyB;
        }
    }
}
