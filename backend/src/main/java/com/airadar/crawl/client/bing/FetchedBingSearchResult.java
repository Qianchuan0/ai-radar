package com.airadar.crawl.client.bing;

/**
 * Bing 搜索结果。
 */
public record FetchedBingSearchResult(
        /**
         * 结果标题
         */
        String title,

        /**
         * 目标 URL
         */
        String url,

        /**
         * 搜索结果摘要
         */
        String snippet,

        /**
         * 显示的 URL（可能为简化形式）
         */
        String displayUrl,

        /**
         * 结果排名
         */
        int rank
) {
}
