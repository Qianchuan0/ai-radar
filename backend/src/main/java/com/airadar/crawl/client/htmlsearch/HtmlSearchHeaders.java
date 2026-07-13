package com.airadar.crawl.client.htmlsearch;

import java.util.Map;

/**
 * HTML 搜索请求的通用 HTTP 头配置。
 * 提供安全的 User-Agent 和标准的 Accept/Accept-Language 头。
 */
public final class HtmlSearchHeaders {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final String DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8";

    private static final String DEFAULT_ACCEPT_LANGUAGE =
            "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7";

    private HtmlSearchHeaders() {
    }

    /**
     * 获取默认的 HTML 搜索请求头映射。
     * 包含 User-Agent、Accept 和 Accept-Language。
     *
     * @return 请求头映射
     */
    public static Map<String, String> defaultHeaders() {
        return Map.of(
                "User-Agent", DEFAULT_USER_AGENT,
                "Accept", DEFAULT_ACCEPT,
                "Accept-Language", DEFAULT_ACCEPT_LANGUAGE
        );
    }

    /**
     * 获取默认的 User-Agent 字符串。
     *
     * @return User-Agent 字符串
     */
    public static String userAgent() {
        return DEFAULT_USER_AGENT;
    }
}
