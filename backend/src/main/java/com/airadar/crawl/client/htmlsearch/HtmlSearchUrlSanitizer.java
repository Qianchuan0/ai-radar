package com.airadar.crawl.client.htmlsearch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * HTML 搜索 URL 清理工具。
 * 处理重定向 URL、验证协议、清理追踪参数。
 */
public final class HtmlSearchUrlSanitizer {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "msclkid", "ref"
    );

    private HtmlSearchUrlSanitizer() {
    }

    /**
     * 清理和验证 URL。
     * 1. 验证协议为 http/https
     * 2. 移除已知的追踪参数
     * 3. 返回规范化的 URL
     *
     * @param url 原始 URL
     * @return 清理后的 URL，如果无效则返回 null
     */
    public static String sanitize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String trimmed = url.trim();

        try {
            URI uri = new URI(trimmed);

            // 验证协议
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
                return null;
            }

            // 移除追踪参数
            String cleanedQuery = removeTrackingParams(uri.getQuery());
            String fragment = uri.getFragment();

            // 重建 URL
            StringBuilder result = new StringBuilder();
            result.append(scheme).append("://");
            result.append(uri.getAuthority());

            if (uri.getPath() != null) {
                result.append(uri.getPath());
            }

            if (cleanedQuery != null && !cleanedQuery.isBlank()) {
                result.append("?").append(cleanedQuery);
            }

            if (fragment != null && !fragment.isBlank()) {
                result.append("#").append(fragment);
            }

            return result.toString();

        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * 从查询字符串中移除追踪参数。
     *
     * @param query 查询字符串
     * @return 移除追踪参数后的查询字符串
     */
    private static String removeTrackingParams(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        String[] params = query.split("&");
        StringBuilder result = new StringBuilder();

        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            String key = keyValue[0];

            if (!TRACKING_PARAMS.contains(key)) {
                if (result.length() > 0) {
                    result.append("&");
                }
                result.append(param);
            }
        }

        return result.length() > 0 ? result.toString() : null;
    }
}
