package com.airadar.crawl.client.htmlsearch;

/**
 * HTML 搜索结果的通用模型。
 * 各个搜索引擎客户端解析后的结果应转换为该格式。
 */
public record HtmlSearchResult(
        /**
         * 搜索结果标题
         */
        String title,

        /**
         * 目标 URL（已清理重定向，验证为 http/https）
         */
        String url,

        /**
         * 搜索结果摘要/描述
         */
        String snippet,

        /**
         * 结果在搜索页中的排名
         */
        int rank
) {
    /**
     * 验证结果是否有效。
     * 标题和 URL 不能为空。
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return title != null && !title.isBlank()
                && url != null && !url.isBlank();
    }
}
