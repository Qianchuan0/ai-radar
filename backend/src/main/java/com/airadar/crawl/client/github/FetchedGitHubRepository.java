package com.airadar.crawl.client.github;

import java.time.Instant;
import java.util.List;

public record FetchedGitHubRepository(
        long repoId,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String ownerLogin,
        String language,
        List<String> topics,
        int stargazersCount,
        int forksCount,
        int watchersCount,
        int openIssuesCount,
        Instant updatedAt
) {
}
