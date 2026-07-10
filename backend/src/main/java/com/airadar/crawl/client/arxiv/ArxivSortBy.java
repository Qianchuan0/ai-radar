package com.airadar.crawl.client.arxiv;

public enum ArxivSortBy {
    RELEVANCE("relevance"),
    LAST_UPDATED_DATE("lastUpdatedDate"),
    SUBMITTED_DATE("submittedDate");

    private final String apiValue;

    ArxivSortBy(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
