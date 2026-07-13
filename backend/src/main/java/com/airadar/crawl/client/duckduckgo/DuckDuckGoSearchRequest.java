package com.airadar.crawl.client.duckduckgo;

/**
 * DuckDuckGo 搜索请求参数。
 */
public record DuckDuckGoSearchRequest(
        /**
         * 搜索查询关键词
         */
        String query,

        /**
         * 返回结果数量限制（1-20）
         */
        int limit,

        /**
         * 区域设置
         */
        String region,

        /**
         * 新鲜度过滤（天数）
         */
        int freshnessDays
) {
    public DuckDuckGoSearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank.");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("Limit must be between 1 and 20.");
        }
        if (freshnessDays < 1 || freshnessDays > 30) {
            throw new IllegalArgumentException("FreshnessDays must be between 1 and 30.");
        }
    }
}
