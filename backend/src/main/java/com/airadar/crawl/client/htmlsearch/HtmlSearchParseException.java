package com.airadar.crawl.client.htmlsearch;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;

/**
 * HTML 搜索解析异常。
 * 当 HTML 解析失败或页面结构不符合预期时抛出。
 */
public class HtmlSearchParseException extends BusinessException {

    public HtmlSearchParseException(String message) {
        super(ErrorCode.CRAWL_UPSTREAM_ERROR, message);
    }

    public HtmlSearchParseException(String message, Throwable cause) {
        super(ErrorCode.CRAWL_UPSTREAM_ERROR, message, cause);
    }

    /**
     * 创建页面被阻止的异常。
     *
     * @param sourceName 来源名称
     * @return 异常实例
     */
    public static HtmlSearchParseException blocked(String sourceName) {
        return new HtmlSearchParseException(
                sourceName + " appears to be blocked or returned a CAPTCHA page."
        );
    }

    /**
     * 创建解析失败的异常。
     *
     * @param sourceName 来源名称
     * @param reason 失败原因
     * @return 异常实例
     */
    public static HtmlSearchParseException parseFailed(String sourceName, String reason) {
        return new HtmlSearchParseException(
                "Failed to parse " + sourceName + " HTML response: " + reason
        );
    }

    /**
     * 创建空结果的异常（当期望有结果时）。
     *
     * @param sourceName 来源名称
     * @return 异常实例
     */
    public static HtmlSearchParseException emptyResult(String sourceName) {
        return new HtmlSearchParseException(
                sourceName + " returned no results (expected at least one)."
        );
    }
}
