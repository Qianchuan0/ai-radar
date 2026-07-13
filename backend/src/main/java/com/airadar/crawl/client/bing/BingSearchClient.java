package com.airadar.crawl.client.bing;

import com.airadar.crawl.client.htmlsearch.HtmlSearchBlockDetector;
import com.airadar.crawl.client.htmlsearch.HtmlSearchHeaders;
import com.airadar.crawl.client.htmlsearch.HtmlSearchParseException;
import com.airadar.crawl.client.htmlsearch.HtmlSearchResult;
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
import java.net.http.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BingSearchClient {

    private static final Logger log = LoggerFactory.getLogger(BingSearchClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final int maxAttempts;
    private final Duration minRequestInterval;
    private final Map<String, Instant> lastRequestTimes = new ConcurrentHashMap<>();

    public BingSearchClient(
            RestClient.Builder restClientBuilder,
            BingSearchProperties properties
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
     * 执行 Bing 搜索。
     *
     * @param request 搜索请求
     * @return 搜索结果列表
     */
    public List<FetchedBingSearchResult> search(BingSearchRequest request) {
        enforceRateLimit();

        String responseBody = executeWithRetry(request);
        return parseHtml(responseBody, request);
    }

    /**
     * 强制执行限流。
     */
    private void enforceRateLimit() {
        Instant now = Instant.now();
        Instant lastRequest = lastRequestTimes.get("bing");

        if (lastRequest != null) {
            Duration elapsed = Duration.between(lastRequest, now);
            if (elapsed.compareTo(minRequestInterval) < 0) {
                try {
                    Thread.sleep(minRequestInterval.minus(elapsed).toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(
                            ErrorCode.CRAWL_UPSTREAM_ERROR,
                            "Bing Search request was interrupted."
                    );
                }
            }
        }

        lastRequestTimes.put("bing", Instant.now());
    }

    /**
     * 执行请求并支持重试。
     */
    private String executeWithRetry(BingSearchRequest request) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String url = buildSearchUrl(request);
                log.debug("Bing Search request (attempt {}): {}", attempt, url);

                return restClient.get()
                        .uri(uriBuilder -> {
                            try {
                                return URI.create(url);
                            } catch (IllegalArgumentException e) {
                                throw new BusinessException(
                                        ErrorCode.CRAWL_UPSTREAM_ERROR,
                                        "Invalid Bing Search URL."
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
                                        "Bing Search returned " + response.getStatusCode()
                                                + " - possible rate limit or block."
                                );
                            }
                            throw new BusinessException(
                                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                                    "Bing Search returned " + response.getStatusCode()
                            );
                        })
                        .body(String.class);

            } catch (ResourceAccessException exception) {
                lastFailure = exception;
                log.warn("Bing Search network error on attempt {}: {}", attempt, exception.getMessage());
            } catch (RestClientResponseException exception) {
                if (!exception.getStatusCode().is5xxServerError()) {
                    throw exception;
                }
                lastFailure = exception;
                log.warn("Bing Search server error on attempt {}: {}", attempt, exception.getMessage());
            } catch (BusinessException exception) {
                throw exception;
            }

            if (attempt < maxAttempts) {
                pauseBeforeRetry(attempt);
            }
        }

        throw new BusinessException(
                ErrorCode.CRAWL_UPSTREAM_ERROR,
                lastFailure == null ? "Bing Search failed after " + maxAttempts + " attempts."
                        : lastFailure.getMessage()
        );
    }

    /**
     * 构建 Bing 搜索 URL。
     */
    private String buildSearchUrl(BingSearchRequest request) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("/search?q=").append(URLEncoder.encode(request.query(), StandardCharsets.UTF_8));

        url.append("&count=").append(request.limit());
        url.append("&mkt=").append(request.market());

        if ("strict".equals(request.safeSearch())) {
            url.append("&safesearch=strict");
        } else if ("off".equals(request.safeSearch())) {
            url.append("&safesearch=off");
        } else {
            url.append("&safesearch=moderate");
        }

        return url.toString();
    }

    /**
     * 解析 HTML 响应。
     */
    private List<FetchedBingSearchResult> parseHtml(String html, BingSearchRequest request) {
        if (html == null || html.isBlank()) {
            throw HtmlSearchParseException.parseFailed("Bing Search", "empty HTML response");
        }

        // 检测是否被阻止
        if (HtmlSearchBlockDetector.isBlocked("", html)) {
            throw HtmlSearchParseException.blocked("Bing Search");
        }

        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            List<FetchedBingSearchResult> results = new ArrayList<>();

            org.jsoup.select.Elements resultItems = doc.select("li.b_algo");

            for (org.jsoup.nodes.Element item : resultItems) {
                String title = null;
                String url = null;
                String snippet = null;
                String displayUrl = null;

                org.jsoup.nodes.Element titleLink = item.selectFirst("h2 a");
                if (titleLink != null) {
                    title = titleLink.text();
                    url = titleLink.attr("href");
                }

                org.jsoup.nodes.Element caption = item.selectFirst(".b_caption p");
                if (caption != null) {
                    snippet = caption.text();
                }

                org.jsoup.nodes.Element displayUrlElement = item.selectFirst(".b_caption .b_attribution cite");
                if (displayUrlElement != null) {
                    displayUrl = displayUrlElement.text();
                }

                // 清理 URL
                String sanitizedUrl = HtmlSearchUrlSanitizer.sanitize(url);

                if (title != null && !title.isBlank() && sanitizedUrl != null) {
                    int rank = results.size() + 1;
                    results.add(new FetchedBingSearchResult(
                            title.trim(),
                            sanitizedUrl,
                            snippet != null ? snippet.trim() : null,
                            displayUrl != null ? displayUrl.trim() : null,
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
                log.warn("Bing Search HTML parsing returned no results but no empty result message found");
            }

            return List.copyOf(results);

        } catch (HtmlSearchParseException e) {
            throw e;
        } catch (Exception e) {
            throw HtmlSearchParseException.parseFailed("Bing Search", e.getMessage());
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
                    "Bing Search request was interrupted."
            );
        }
    }
}
