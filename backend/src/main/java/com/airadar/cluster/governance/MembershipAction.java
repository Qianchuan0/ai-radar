package com.airadar.cluster.governance;

/**
 * Type of membership mutation recorded in {@code cluster_membership_history}.
 *
 * <p>Each value corresponds to one governance operation:
 * <ul>
 *   <li>{@link #ADD} — item added to a cluster without a source (e.g. brand
 *       new cluster created by governance)</li>
 *   <li>{@link #REMOVE} — item removed from a cluster without landing in a
 *       new one</li>
 *   <li>{@link #MOVE} — single item moved from one cluster to another</li>
 *   <li>{@link #MERGE} — every active member of the loser cluster moved into
 *       the winner cluster</li>
 *   <li>{@link #SPLIT} — selected members moved out of a cluster into a new
 *       or different target cluster</li>
 *   <li>{@link #RECLUSTER} — item re-evaluated by the V2 strategy; no online
 *       membership change because the result still requires governance
 *       confirmation</li>
 * </ul>
 *
 * <p>Values are persisted verbatim into the {@code action} column and
 * constrained by migration V10.
 */
public enum MembershipAction {
    ADD,
    REMOVE,
    MOVE,
    MERGE,
    SPLIT,
    RECLUSTER
}
