package com.airadar.crawl.client.arxiv;

public enum ArxivSortOrder {
    ASCENDING("ascending"),
    DESCENDING("descending");

    private final String apiValue;

    ArxivSortOrder(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
