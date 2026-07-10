package com.airadar.crawl.client.arxiv;

public record ArxivSearchRequest(
        String searchQuery,
        int start,
        int maxResults,
        ArxivSortBy sortBy,
        ArxivSortOrder sortOrder
) {

    public ArxivSearchRequest {
        if (searchQuery == null || searchQuery.isBlank()) {
            throw new IllegalArgumentException("arXiv searchQuery must not be blank.");
        }
        if (start < 0) {
            throw new IllegalArgumentException("arXiv start must be zero or positive.");
        }
        if (maxResults < 1 || maxResults > 100) {
            throw new IllegalArgumentException("arXiv maxResults must be between 1 and 100.");
        }
        if (sortBy == null) {
            throw new IllegalArgumentException("arXiv sortBy must not be null.");
        }
        if (sortOrder == null) {
            throw new IllegalArgumentException("arXiv sortOrder must not be null.");
        }
    }
}
