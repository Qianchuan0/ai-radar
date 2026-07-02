package com.airadar.scoring.model;

public record ScoreBreakdown(
        double points,
        double comments,
        double freshness,
        double keyword,
        double clusterEvidence
) {

    public double total() {
        return points + comments + freshness + keyword + clusterEvidence;
    }
}
