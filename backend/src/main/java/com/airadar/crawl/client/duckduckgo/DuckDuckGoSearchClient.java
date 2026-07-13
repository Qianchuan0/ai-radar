package com.airadar.crawl.client.duckduckgo;

import com.airadar.crawl.client.htmlsearch.HtmlSearchBlockDetector;
import com.airadar.crawl.client.htmlsearch.HtmlSearchHeaders;
import com.airadar.crawl.client.htmlsearch.HtmlSearchParseException;
import com.airadar.crawl.client.htmlsearch.HtmlSearchUrlSanitizer;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DuckDuckGoSearchClient {

    private static final Logger log = LoggerFactory.getLogger(DuckDuckGoSearchClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final int maxAttempts;
    private final Duration minRequestInterval;
    private final Map<String, Instant> lastRequestTimes = new ConcurrentHashMap<>();

    public DuckDuckGoSearchClient(
            RestClient.Builder restClientBuilder,
            DuckDuckGoSearchProperties properties
    ) {
        this.baseUrl = properties.baseUrl();
        this.maxAttempts = properties.maxAttempts();
        this.minRequestInterval = properties.minRequestInterval();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 执行 DuckDuckGo 搜索。
     *
     * @param request 搜索请求
     * @return 搜索结果列表
     */
    public List<FetchedDuckDuckGoSearchResult> search(DuckDuckGoSearchRequest request) {
        enforceRateLimit();

        String responseBody = executeWithRetry(request);
        return parseHtml(responseBody, request);
    }

    /**
     * 强制执行限流。
     */
    private void enforceRateLimit() {
        Instant now = Instant.now();
        Instant lastRequest = lastRequestTimes.get("duckduckgo");

        if (lastRequest != null) {
            Duration elapsed = Duration.between(lastRequest, now);
            if (elapsed.compareTo(minRequestInterval) < 0) {
                try {
                    Thread.sleep(minRequestInterval.minus(elapsed).toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(
                            ErrorCode.CRAWL_UPSTREAM_ERROR,
                            "DuckDuckGo Search request was interrupted."
                    );
                }
            }
        }

        lastRequestTimes.put("duckduckgo", Instant.now());
    }

    /**
     * 执行请求并支持重试。
     */
    private String executeWithRetry(DuckDuckGoSearchRequest request) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String url = buildSearchUrl(request);
                log.debug("DuckDuckGo Search request (attempt {}): {}", attempt, url);

                return restClient.get()
                        .uri(uriBuilder -> {
                            try {
                                return URI.create(url);
                            } catch (IllegalArgumentException e) {
                                throw new BusinessException(
                                        ErrorCode.CRAWL_UPSTREAM_ERROR,
                                        "Invalid DuckDuckGo Search URL."
                                );
                            }
                        })
                        .headers(headers -> HtmlSearchHeaders.defaultHeaders().forEach(headers::add))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            if (response.getStatusCode().value() == 403
                                    || response.getStatusCode().value() == 429) {
                                throw new BusinessException(
                                        ErrorCode.CRAWL_UPSTREAM_ERROR,
                                        "DuckDuckGo Search returned " + response.getStatusCode()
                                                + " - possible rate limit or block."
                                );
                            }
                            throw new BusinessException(
                                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                                    "DuckDuckGo Search returned " + response.getStatusCode()
                            );
                        })
                        .body(String.class);

            } catch (ResourceAccessException exception) {
                lastFailure = exception;
                log.warn("DuckDuckGo Search network error on attempt {}: {}", attempt, exception.getMessage());
            } catch (RestClientResponseException exception) {
                if (!exception.getStatusCode().is5xxServerError()) {
                    throw exception;
                }
                lastFailure = exception;
                log.warn("DuckDuckGo Search server error on attempt {}: {}", attempt, exception.getMessage());
            } catch (BusinessException exception) {
                throw exception;
            }

            if (attempt < maxAttempts) {
                pauseBeforeRetry(attempt);
            }
        }

        throw new BusinessException(
                ErrorCode.CRAWL_UPSTREAM_ERROR,
                lastFailure == null ? "DuckDuckGo Search failed after " + maxAttempts + " attempts."
                        : lastFailure.getMessage()
        );
    }

    /**
     * 构建 DuckDuckGo 搜索 URL。
     */
    private String buildSearchUrl(DuckDuckGoSearchRequest request) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("/html/?q=").append(URLEncoder.encode(request.query(), StandardCharsets.UTF_8));

        url.append("&kl=").append(request.region());

        return url.toString();
    }

    /**
     * 解析 HTML 响应。
     */
    private List<FetchedDuckDuckGoSearchResult> parseHtml(String html, DuckDuckGoSearchRequest request) {
        if (html == null || html.isBlank()) {
            throw HtmlSearchParseException.parseFailed("DuckDuckGo Search", "empty HTML response");
        }

        // 检测是否被阻止
        if (HtmlSearchBlockDetector.isBlocked("", html)) {
            throw HtmlSearchParseException.blocked("DuckDuckGo Search");
        }

        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            List<FetchedDuckDuckGoSearchResult> results = new ArrayList<>();

            org.jsoup.select.Elements resultItems = doc.select(".result");

            for (org.jsoup.nodes.Element item : resultItems) {
                String title = null;
                String rawUrl = null;
                String url = null;
                String snippet = null;

                org.jsoup.nodes.Element titleLink = item.selectFirst(".result__title a");
                if (titleLink != null) {
                    title = titleLink.text();
                    rawUrl = titleLink.attr("href");
                }

                org.jsoup.nodes.Element snippetElement = item.selectFirst(".result__snippet");
                if (snippetElement != null) {
                    snippet = snippetElement.text();
                }

                // 处理 DuckDuckGo 的重定向 URL
                if (rawUrl != null && !rawUrl.isBlank()) {
                    url = decodeRedirectUrl(rawUrl);
                }

                // 清理 URL
                String sanitizedUrl = HtmlSearchUrlSanitizer.sanitize(url);

                if (title != null && !title.isBlank() && sanitizedUrl != null) {
                    int rank = results.size() + 1;
                    results.add(new FetchedDuckDuckGoSearchResult(
                            title.trim(),
                            rawUrl,
                            sanitizedUrl,
                            snippet != null ? snippet.trim() : null,
                            rank
                    ));

                    if (results.size() >= request.limit()) {
                        break;
                    }
                }
            }

            if (results.isEmpty()) {
                // 检查是否为空结果提示
                if (HtmlSearchBlockDetector.isEmptyResult(html)) {
                    return List.of(); // 合法空结果
                }
                // 解析失败但没有明确提示
                log.warn("DuckDuckGo Search HTML parsing returned no results but no empty result message found");
            }

            return List.copyOf(results);

        } catch (HtmlSearchParseException e) {
            throw e;
        } catch (Exception e) {
            throw HtmlSearchParseException.parseFailed("DuckDuckGo Search", e.getMessage());
        }
    }

    /**
     * 解码 DuckDuckGo 的重定向 URL。
     * DuckDuckGo 使用 `uddg` 参数存储真实 URL。
     */
    private String decodeRedirectUrl(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return null;
        }

        try {
            // 检查是否包含 uddg 参数
            if (redirectUrl.contains("uddg=")) {
                String[] parts = redirectUrl.split("uddg=");
                if (parts.length > 1) {
                    String encoded = parts[1].split("&")[0];
                    return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                }
            }

            // 如果不是重定向格式，直接返回
            return redirectUrl;
        } catch (Exception e) {
            log.debug("Failed to decode DuckDuckGo redirect URL: {}", e.getMessage());
            return redirectUrl;
        }
    }

    /**
     * 重试前暂停。
     */
    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                    "DuckDuckGo Search request was interrupted."
            );
        }
    }
}
