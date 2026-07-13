package com.airadar.crawl.client.htmlsearch;

/**
 * HTML 搜索页面阻止检测器。
 * 识别验证码页、bot challenge 页面和明显被阻止的页面。
 */
public final class HtmlSearchBlockDetector {

    private static final String[] CAPTCHA_KEYWORDS = {
            "captcha", "recaptcha", "hcaptcha", "cf-challenge", "cloudflare",
            "verify you are human", "human verification", "prove you're not a robot",
            "access denied", "you have been blocked", "unusual traffic",
            "please complete the security check", "checking your browser",
            "enable javascript", "javascript is disabled"
    };

    private static final String[] EMPTY_RESULT_KEYWORDS = {
            "no results found", "no matches found", "your search did not match any documents",
            "we couldn't find anything", "nothing matched your search",
            "no results for", "did not match"
    };

    private HtmlSearchBlockDetector() {
    }

    /**
     * 检测页面是否被阻止或显示验证码。
     * 通过页面标题和内容中的关键词进行判断。
     *
     * @param pageTitle 页面标题
     * @param pageContent 页面内容（可以是 HTML 或文本）
     * @return 是否被阻止
     */
    public static boolean isBlocked(String pageTitle, String pageContent) {
        String combinedText = (pageTitle + " " + (pageContent != null ? pageContent : "")).toLowerCase();

        for (String keyword : CAPTCHA_KEYWORDS) {
            if (combinedText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测页面是否表示空搜索结果。
     *
     * @param pageContent 页面内容
     * @return 是否为空结果
     */
    public static boolean isEmptyResult(String pageContent) {
        if (pageContent == null || pageContent.isBlank()) {
            return false; // 空页面不算"空结果"提示
        }

        String lowerContent = pageContent.toLowerCase();

        for (String keyword : EMPTY_RESULT_KEYWORDS) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测页面是否可能是需要 JavaScript 的动态页面。
     *
     * @param pageContent 页面内容（HTML）
     * @return 是否需要 JavaScript
     */
    public static boolean requiresJavaScript(String pageContent) {
        if (pageContent == null || pageContent.isBlank()) {
            return false;
        }

        String lowerContent = pageContent.toLowerCase();

        // 检查是否有 noscript 标签提示需要 JS
        return lowerContent.contains("<noscript") &&
                (lowerContent.contains("javascript") || lowerContent.contains("enable javascript"));
    }
}
