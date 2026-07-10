package com.airadar.crawl.client.github;

public record GitHubSearchRequest(
        String query,
        String sort,
        String order,
        int perPage,
        int page
) {
}
