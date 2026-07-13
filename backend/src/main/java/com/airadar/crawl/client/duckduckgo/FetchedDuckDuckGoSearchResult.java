package com.airadar.crawl.client.duckduckgo;

/**
 * DuckDuckGo 搜索结果。
 */
public record FetchedDuckDuckGoSearchResult(
        /**
         * 结果标题
         */
        String title,

        /**
         * 原始 URL（可能包含重定向）
         */
        String rawUrl,

        /**
         * 清理后的目标 URL
         */
        String url,

        /**
         * 搜索结果摘要
         */
        String snippet,

        /**
         * 结果排名
         */
        int rank
) {
}
