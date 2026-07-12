# Phase 12A: Sogou Search Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `SOGOU_SEARCH` as the first Chinese platform source via the Tencent Cloud Web Search API (wsa), integrated into the existing crawl-to-cluster closed loop.

**Architecture:** Follow the existing source connector pattern (`Properties -> Request -> Fetched -> Client -> Collector -> Normalizer`). A reusable `TencentCloudV3Signer` utility handles TC3-HMAC-SHA256 signature for the Tencent Cloud API. The client uses Spring `RestClient` with POST JSON, parsing the `Pages` JSON string array response.

**Tech Stack:** Java 17, Spring Boot 3.5.15, RestClient, Jackson, JUnit 5, Testcontainers, Vue 3, TypeScript, Vitest

## Global Constraints

- Secrets only via environment variables, never in `source_config.config_payload`
- No web scraping, no Tencent Cloud SDK dependency, no new infrastructure
- All new `@ConfigurationProperties` classes are auto-scanned via `@ConfigurationPropertiesScan` on `AiRadarApplication`
- Frontend UI language is Chinese (decision-log #19)
- No auto-commit, no auto-push during implementation
- Tests stay offline (no real API calls)

---

### Task 1: Add SOGOU_SEARCH to SourceType and CRAWL_PROVIDER_NOT_CONFIGURED to ErrorCode

**Files:**
- Modify: `backend/src/main/java/com/airadar/source/model/SourceType.java`
- Modify: `backend/src/main/java/com/airadar/common/exception/ErrorCode.java`

**Interfaces:**
- Produces: `SourceType.SOGOU_SEARCH` enum value used by all subsequent tasks
- Produces: `ErrorCode.CRAWL_PROVIDER_NOT_CONFIGURED` used by Task 4 and Task 5

- [ ] **Step 1: Add SOGOU_SEARCH to SourceType**

```java
package com.airadar.source.model;

public enum SourceType {
    ARXIV,
    HACKER_NEWS,
    GITHUB,
    HUGGING_FACE,
    SOGOU_SEARCH
}
```

- [ ] **Step 2: Add CRAWL_PROVIDER_NOT_CONFIGURED to ErrorCode**

Add before `INTERNAL_ERROR`:

```java
    CRAWL_PROVIDER_NOT_CONFIGURED("CRAWL.PROVIDER_NOT_CONFIGURED", "Crawl provider credentials are not configured.", HttpStatus.BAD_GATEWAY),
```

- [ ] **Step 3: Verify backend compiles**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/main/java/com/airadar/source/model/SourceType.java backend/src/main/java/com/airadar/common/exception/ErrorCode.java
git commit -m "feat: add SOGOU_SEARCH source type and CRAWL_PROVIDER_NOT_CONFIGURED error code"
```

---

### Task 2: TencentCloudV3Signer Utility

**Files:**
- Create: `backend/src/main/java/com/airadar/crawl/client/support/TencentCloudV3Signer.java`
- Create: `backend/src/main/java/com/airadar/crawl/client/support/SignedRequest.java`
- Test: `backend/src/test/java/com/airadar/crawl/client/support/TencentCloudV3SignerTest.java`

**Interfaces:**
- Produces: `TencentCloudV3Signer.sign(secretId, secretKey, service, host, action, version, payload, timestamp)` returning `SignedRequest(authorization, timestamp)`
- Used by: Task 4 (SogouSearchClient)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/airadar/crawl/client/support/TencentCloudV3SignerTest.java`:

```java
package com.airadar.crawl.client.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class TencentCloudV3SignerTest {

    @Test
    void shouldProduceValidAuthorizationHeader() {
        String payload = "{\"Query\":\"AI\"}";
        Instant timestamp = Instant.ofEpochSecond(1749859200L);

        SignedRequest signed = TencentCloudV3Signer.sign(
                "AKIDxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "Gu5t9xGARNpq86cd98joQYCN3xxxxxx",
                "wsa",
                "wsa.tencentcloudapi.com",
                "SearchPro",
                "2025-05-08",
                payload,
                timestamp
        );

        assertThat(signed.timestamp()).isEqualTo("1749859200");
        assertThat(signed.authorization()).startsWith("TC3-HMAC-SHA256 Credential=AKIDxxxxxxxxxxxxxxxxxxxxxxxxxxx/");
        assertThat(signed.authorization()).contains("SignedHeaders=content-type;host;x-tc-action");
        assertThat(signed.authorization()).contains("Signature=");
    }

    @Test
    void shouldProduceDeterministicSignatureForSameInputs() {
        String payload = "{\"Query\":\"test\"}";
        Instant timestamp = Instant.ofEpochSecond(1749859200L);

        SignedRequest first = TencentCloudV3Signer.sign("id", "key", "wsa", "wsa.tencentcloudapi.com", "SearchPro", "2025-05-08", payload, timestamp);
        SignedRequest second = TencentCloudV3Signer.sign("id", "key", "wsa", "wsa.tencentcloudapi.com", "SearchPro", "2025-05-08", payload, timestamp);

        assertThat(first.authorization()).isEqualTo(second.authorization());
    }

    @Test
    void shouldChangeSignatureWhenPayloadChanges() {
        Instant timestamp = Instant.ofEpochSecond(1749859200L);

        SignedRequest first = TencentCloudV3Signer.sign("id", "key", "wsa", "wsa.tencentcloudapi.com", "SearchPro", "2025-05-08", "{\"Query\":\"A\"}", timestamp);
        SignedRequest second = TencentCloudV3Signer.sign("id", "key", "wsa", "wsa.tencentcloudapi.com", "SearchPro", "2025-05-08", "{\"Query\":\"B\"}", timestamp);

        assertThat(first.authorization()).isNotEqualTo(second.authorization());
    }

    @Test
    void shouldIncludeCredentialScopeWithDateAndService() {
        Instant timestamp = Instant.ofEpochSecond(1749859200L);
        String expectedDate = timestamp.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE);

        SignedRequest signed = TencentCloudV3Signer.sign("id", "key", "wsa", "wsa.tencentcloudapi.com", "SearchPro", "2025-05-08", "{}", timestamp);

        assertThat(signed.authorization()).contains(expectedDate + "/wsa/tc3_request");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd test -Dtest=TencentCloudV3SignerTest -q`
Expected: FAIL (class not found)

- [ ] **Step 3: Create SignedRequest record**

Create `backend/src/main/java/com/airadar/crawl/client/support/SignedRequest.java`:

```java
package com.airadar.crawl.client.support;

public record SignedRequest(String authorization, String timestamp) {
}
```

- [ ] **Step 4: Create TencentCloudV3Signer**

Create `backend/src/main/java/com/airadar/crawl/client/support/TencentCloudV3Signer.java`:

```java
package com.airadar.crawl.client.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class TencentCloudV3Signer {

    private TencentCloudV3Signer() {
    }

    public static SignedRequest sign(
            String secretId,
            String secretKey,
            String service,
            String host,
            String action,
            String version,
            String payload,
            Instant timestamp
    ) {
        String timestampStr = String.valueOf(timestamp.getEpochSecond());
        String date = timestamp.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE);

        String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + action.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String hashedPayload = sha256Hex(payload);

        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload;

        String credentialScope = date + "/" + service + "/tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = "TC3-HMAC-SHA256\n" + timestampStr + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));

        String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        return new SignedRequest(authorization, timestampStr);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is not available.", e);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd test -Dtest=TencentCloudV3SignerTest -q`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/main/java/com/airadar/crawl/client/support/ backend/src/test/java/com/airadar/crawl/client/support/
git commit -m "feat: add TencentCloudV3Signer for TC3-HMAC-SHA256 signature"
```

---

### Task 3: Sogou Search Data Records

**Files:**
- Create: `backend/src/main/java/com/airadar/crawl/client/sogou/SogouSearchProperties.java`
- Create: `backend/src/main/java/com/airadar/crawl/client/sogou/SogouSearchRequest.java`
- Create: `backend/src/main/java/com/airadar/crawl/client/sogou/FetchedSogouSearchResult.java`

**Interfaces:**
- Produces: `SogouSearchProperties` record with `baseUrl`, `connectTimeout`, `readTimeout`, `maxAttempts`, `secretId`, `secretKey`
- Produces: `SogouSearchRequest` record with `query`, `cnt`, `mode`, `site`, `freshness`, `fromTime`, `toTime`
- Produces: `FetchedSogouSearchResult` record with `title`, `url`, `passage`, `content`, `site`, `score`, `publishedAt`, `rank`
- Used by: Task 4 (Client), Task 5 (Collector)

- [ ] **Step 1: Create SogouSearchProperties**

Create `backend/src/main/java/com/airadar/crawl/client/sogou/SogouSearchProperties.java`:

```java
package com.airadar.crawl.client.sogou;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.sogou-search")
public record SogouSearchProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        String secretId,
        String secretKey
) {

    public SogouSearchProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Sogou Search maxAttempts must be between 1 and 3.");
        }
    }
}
```

- [ ] **Step 2: Create SogouSearchRequest**

Create `backend/src/main/java/com/airadar/crawl/client/sogou/SogouSearchRequest.java`:

```java
package com.airadar.crawl.client.sogou;

public record SogouSearchRequest(
        String query,
        int cnt,
        int mode,
        String site,
        String freshness,
        Long fromTime,
        Long toTime
) {
}
```

- [ ] **Step 3: Create FetchedSogouSearchResult**

Create `backend/src/main/java/com/airadar/crawl/client/sogou/FetchedSogouSearchResult.java`:

```java
package com.airadar.crawl.client.sogou;

import java.time.Instant;

public record FetchedSogouSearchResult(
        String title,
        String url,
        String passage,
        String content,
        String site,
        double score,
        Instant publishedAt,
        int rank
) {
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/main/java/com/airadar/crawl/client/sogou/
git commit -m "feat: add Sogou search properties, request, and fetched result records"
```

---

### Task 4: SogouSearchClient

**Files:**
- Create: `backend/src/main/java/com/airadar/crawl/client/sogou/SogouSearchClient.java`
- Test: `backend/src/test/java/com/airadar/crawl/client/sogou/SogouSearchClientTest.java`

**Interfaces:**
- Consumes: `TencentCloudV3Signer` (Task 2), `SogouSearchProperties`, `SogouSearchRequest`, `FetchedSogouSearchResult` (Task 3)
- Produces: `SogouSearchClient.search(SogouSearchRequest)` returning `List<FetchedSogouSearchResult>`
- Used by: Task 5 (Collector)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/airadar/crawl/client/sogou/SogouSearchClientTest.java`:

```java
package com.airadar.crawl.client.sogou;

import com.airadar.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SogouSearchClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendSignedPostAndParsePages() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> actionHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            actionHeader.set(exchange.getRequestHeaders().getFirst("X-TC-Action"));
            writeJson(exchange, 200, """
                    {
                      "Response": {
                        "Query": "大模型",
                        "Pages": [
                          "{\\"title\\":\\"大模型发展报告\\",\\"url\\":\\"https://example.com/article1\\",\\"passage\\":\\"大模型最新进展\\",\\"site\\":\\"示例站\\",\\"score\\":0.95,\\"date\\":\\"2026/07/12 10:00:00\\"}",
                          "{\\"title\\":\\"AI智能体应用\\",\\"url\\":\\"https://example.com/article2\\",\\"passage\\":\\"智能体技术\\",\\"site\\":\\"科技网\\",\\"score\\":0.80,\\"date\\":\\"2026/07/11 15:30:00\\"}"
                        ],
                        "Version": "standard",
                        "RequestId": "req-123"
                      }
                    }
                    """);
        });
        server.start();

        SogouSearchClient client = new SogouSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new SogouSearchProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "test-secret-id",
                        "test-secret-key"
                )
        );

        List<FetchedSogouSearchResult> results = client.search(new SogouSearchRequest(
                "大模型", 20, 0, "", "", null, null
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).title()).isEqualTo("大模型发展报告");
        assertThat(results.get(0).url()).isEqualTo("https://example.com/article1");
        assertThat(results.get(0).passage()).isEqualTo("大模型最新进展");
        assertThat(results.get(0).site()).isEqualTo("示例站");
        assertThat(results.get(0).score()).isEqualTo(0.95);
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(0).publishedAt()).isNotNull();
        assertThat(results.get(1).rank()).isEqualTo(2);
        assertThat(authorizationHeader.get()).startsWith("TC3-HMAC-SHA256 ");
        assertThat(actionHeader.get()).isEqualTo("SearchPro");
        assertThat(requestBody.get()).contains("\"Query\":\"大模型\"");
        assertThat(requestBody.get()).contains("\"Cnt\":20");
    }

    @Test
    void shouldReturnEmptyListWhenPagesIsEmpty() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"Response":{"Query":"test","Pages":[],"Version":"standard","RequestId":"r"}}
                """));
        server.start();

        SogouSearchClient client = createClient();

        List<FetchedSogouSearchResult> results = client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        ));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldThrowOnUpstreamError() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 500, """
                {"Response":{"Error":{"Code":"InternalError","Message":"boom"},"RequestId":"r"}}
                """));
        server.start();

        SogouSearchClient client = createClient();

        assertThatThrownBy(() -> client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        )))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowProviderNotConfiguredWhenCredentialsAreBlank() {
        SogouSearchClient client = new SogouSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new SogouSearchProperties(
                        "http://localhost:0",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "",
                        ""
                )
        );

        assertThatThrownBy(() -> client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldSkipPagesWithMissingTitleOrUrl() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"Response":{"Query":"test","Pages":["{\\"title\\":\\"\\",\\"url\\":\\"https://example.com/a\\",\\"passage\\":\\"p\\",\\"site\\":\\"s\\",\\"score\\":0.5}","{\\"title\\":\\"ok\\",\\"url\\":\\"https://example.com/b\\",\\"passage\\":\\"p\\",\\"site\\":\\"s\\",\\"score\\":0.5}"],"Version":"standard","RequestId":"r"}}
                """));
        server.start();

        SogouSearchClient client = createClient();

        List<FetchedSogouSearchResult> results = client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("ok");
    }

    private SogouSearchClient createClient() {
        return new SogouSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new SogouSearchProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "test-secret-id",
                        "test-secret-key"
                )
        );
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBody);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd test -Dtest=SogouSearchClientTest -q`
Expected: FAIL (class not found)

- [ ] **Step 3: Create SogouSearchClient**

Create `backend/src/main/java/com/airadar/crawl/client/sogou/SogouSearchClient.java`:

```java
package com.airadar.crawl.client.sogou;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.crawl.client.support.SignedRequest;
import com.airadar.crawl.client.support.TencentCloudV3Signer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class SogouSearchClient {

    private static final String SERVICE = "wsa";
    private static final String ACTION = "SearchPro";
    private static final String VERSION = "2025-05-08";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final String secretId;
    private final String secretKey;
    private final String host;

    public SogouSearchClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            SogouSearchProperties properties
    ) {
        this.host = extractHost(properties.baseUrl());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.maxAttempts = properties.maxAttempts();
        this.secretId = properties.secretId();
        this.secretKey = properties.secretKey();
    }

    public List<FetchedSogouSearchResult> search(SogouSearchRequest request) {
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(
                    ErrorCode.CRAWL_PROVIDER_NOT_CONFIGURED,
                    "Sogou Search secret-id or secret-key is not configured."
            );
        }
        String payload = buildPayload(request);
        String responseBody = executeWithRetry(payload);
        return parseResponse(responseBody);
    }

    private String buildPayload(SogouSearchRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("Query", request.query());
        payload.put("Cnt", request.cnt());
        payload.put("Mode", request.mode());
        if (request.site() != null && !request.site().isBlank()) {
            payload.put("Site", request.site());
        }
        if (request.freshness() != null && !request.freshness().isBlank()) {
            payload.put("Freshness", request.freshness());
        }
        if (request.fromTime() != null) {
            payload.put("FromTime", request.fromTime());
        }
        if (request.toTime() != null) {
            payload.put("ToTime", request.toTime());
        }
        return payload.toString();
    }

    private String executeWithRetry(String payload) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Instant now = Instant.now();
                SignedRequest signed = TencentCloudV3Signer.sign(
                        secretId, secretKey, SERVICE, host, ACTION, VERSION, payload, now
                );
                return restClient.post()
                        .uri("/")
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("X-TC-Action", ACTION)
                        .header("X-TC-Version", VERSION)
                        .header("X-TC-Timestamp", signed.timestamp())
                        .header("Authorization", signed.authorization())
                        .body(payload)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new BusinessException(
                                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                                    "Sogou Search returned " + response.getStatusCode()
                            );
                        })
                        .body(String.class);
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
            } catch (RestClientResponseException exception) {
                if (!exception.getStatusCode().is5xxServerError()) {
                    throw exception;
                }
                lastFailure = exception;
            } catch (BusinessException exception) {
                throw exception;
            }
            if (attempt < maxAttempts) {
                pauseBeforeRetry(attempt);
            }
        }
        throw new BusinessException(
                ErrorCode.CRAWL_UPSTREAM_ERROR,
                lastFailure == null ? ErrorCode.CRAWL_UPSTREAM_ERROR.getDefaultMessage() : lastFailure.getMessage()
        );
    }

    private List<FetchedSogouSearchResult> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.path("Response");
            JsonNode pages = response.path("Pages");
            if (!pages.isArray()) {
                return List.of();
            }
            List<FetchedSogouSearchResult> results = new ArrayList<>(pages.size());
            int rank = 1;
            for (JsonNode pageNode : pages) {
                String pageJson = pageNode.asText("");
                if (pageJson.isBlank()) {
                    rank++;
                    continue;
                }
                try {
                    JsonNode page = objectMapper.readTree(pageJson);
                    String title = nullableText(page, "title");
                    String url = nullableText(page, "url");
                    if (title == null || title.isBlank() || url == null || url.isBlank()) {
                        rank++;
                        continue;
                    }
                    results.add(new FetchedSogouSearchResult(
                            title,
                            url,
                            nullableText(page, "passage"),
                            nullableText(page, "content"),
                            nullableText(page, "site"),
                            page.path("score").asDouble(0.0),
                            parseDate(nullableText(page, "date")),
                            rank
                    ));
                } catch (Exception ignored) {
                    // Skip unparseable page entries
                }
                rank++;
            }
            return List.copyOf(results);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid Sogou Search JSON response.");
        }
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.from(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                    .withZone(ZoneOffset.UTC)
                    .parse(value));
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored2) {
                return null;
            }
        }
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null ? null : text.trim();
    }

    private String extractHost(String baseUrl) {
        String withoutScheme = baseUrl.replaceFirst("^https?://", "");
        return withoutScheme.split("/")[0];
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Sogou Search request was interrupted.");
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd test -Dtest=SogouSearchClientTest -q`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/main/java/com/airadar/crawl/client/sogou/SogouSearchClient.java backend/src/test/java/com/airadar/crawl/client/sogou/SogouSearchClientTest.java
git commit -m "feat: add SogouSearchClient with TC3 signature and Pages parsing"
```

---

### Task 5: SogouSearchCollector

**Files:**
- Create: `backend/src/main/java/com/airadar/crawl/collector/sogou/SogouSearchCollector.java`

**Interfaces:**
- Consumes: `SogouSearchClient` (Task 4), `SogouSearchProperties` (Task 3), `SourceType.SOGOU_SEARCH` (Task 1)
- Produces: `SogouSearchCollector.collect(SourceConfigEntity)` returning `CollectionBatch`
- Used by: Task 8 (Integration test), registered automatically via `CollectorRegistry`

- [ ] **Step 1: Create SogouSearchCollector**

Create `backend/src/main/java/com/airadar/crawl/collector/sogou/SogouSearchCollector.java`:

```java
package com.airadar.crawl.collector.sogou;

import com.airadar.crawl.client.sogou.FetchedSogouSearchResult;
import com.airadar.crawl.client.sogou.SogouSearchClient;
import com.airadar.crawl.client.sogou.SogouSearchRequest;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.CollectionError;
import com.airadar.crawl.collector.SourceCollector;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class SogouSearchCollector implements SourceCollector {

    private static final int DEFAULT_CNT = 10;
    private static final int DEFAULT_MODE = 0;

    private final SogouSearchClient sogouSearchClient;
    private final ObjectMapper objectMapper;

    public SogouSearchCollector(SogouSearchClient sogouSearchClient, ObjectMapper objectMapper) {
        this.sogouSearchClient = sogouSearchClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.SOGOU_SEARCH;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        String query = config.path("query").asText("").trim();
        int cnt = config.path("cnt").asInt(DEFAULT_CNT);
        int mode = config.path("mode").asInt(DEFAULT_MODE);
        String site = config.path("site").asText("");
        String freshness = config.path("freshness").asText("");

        List<FetchedSogouSearchResult> results = sogouSearchClient.search(new SogouSearchRequest(
                query, cnt, mode, site, freshness, null, null
        ));

        int totalCount = results.size();
        List<CollectedItem> items = new ArrayList<>(totalCount);
        for (FetchedSogouSearchResult result : results) {
            String externalId = sha256Hex(result.url());
            items.add(new CollectedItem(
                    externalId,
                    result.url(),
                    toRawPayload(result, query, totalCount),
                    result.publishedAt(),
                    Instant.now()
            ));
        }
        return new CollectionBatch(items, List.of());
    }

    private JsonNode toRawPayload(FetchedSogouSearchResult result, String query, int totalCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", result.title());
        payload.put("url", result.url());
        payload.put("passage", result.passage());
        payload.put("content", result.content());
        payload.put("site", result.site());
        payload.put("score", result.score());
        payload.put("date", result.publishedAt() == null ? null : result.publishedAt().toString());
        payload.put("rank", result.rank());
        payload.put("totalCount", totalCount);
        payload.put("query", query);
        return payload;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/main/java/com/airadar/crawl/collector/sogou/SogouSearchCollector.java
git commit -m "feat: add SogouSearchCollector"
```

---

### Task 6: SogouSearchHotItemNormalizer

**Files:**
- Create: `backend/src/main/java/com/airadar/item/normalizer/SogouSearchHotItemNormalizer.java`

**Interfaces:**
- Consumes: `SourceType.SOGOU_SEARCH` (Task 1), `UrlCanonicalizer` (existing)
- Produces: `SogouSearchHotItemNormalizer.normalize(RawItemEntity, SourceConfigEntity)` returning `Optional<NormalizedHotItem>`
- Registered automatically via `HotItemNormalizerRegistry`

- [ ] **Step 1: Create SogouSearchHotItemNormalizer**

Create `backend/src/main/java/com/airadar/item/normalizer/SogouSearchHotItemNormalizer.java`:

```java
package com.airadar.item.normalizer;

import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.model.NormalizedHotItem;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

@Component
public class SogouSearchHotItemNormalizer implements HotItemNormalizer {

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public SogouSearchHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.SOGOU_SEARCH;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String title = cleanText(payload.path("title").asText(""));
        String url = cleanText(payload.path("url").asText(""));
        if (title.isBlank() || url.isBlank()) {
            return Optional.empty();
        }

        String sourceUrl = urlCanonicalizer.canonicalize(url);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        int rank = payload.path("rank").asInt(1);
        int totalCount = payload.path("totalCount").asInt(rank);
        int points = Math.max(1, totalCount - rank + 1);

        ArrayNode tags = objectMapper.createArrayNode();
        Set<String> seenTags = new HashSet<>();
        String query = cleanText(payload.path("query").asText(""));
        if (!query.isBlank()) {
            for (String keyword : query.split("\\s+OR\\s+|\\s+")) {
                addTag(tags, seenTags, keyword);
            }
        }
        String site = cleanText(payload.path("site").asText(""));
        addTag(tags, seenTags, site);

        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", points);
        metrics.put("commentsCount", 0);
        metrics.put("rank", rank);
        metrics.put("score", payload.path("score").asDouble(0.0));
        if (!site.isBlank()) {
            metrics.put("site", site);
        }

        String passage = cleanText(payload.path("passage").asText(""));
        String content = cleanText(payload.path("content").asText(""));
        String summary = !passage.isBlank() ? passage : content;
        if (summary.isBlank()) {
            summary = null;
        } else if (summary.length() > 2000) {
            summary = summary.substring(0, 2000);
        }

        String author = site.isBlank() ? null : site;

        return Optional.of(new NormalizedHotItem(
                "SEARCH_RESULT",
                title,
                summary,
                sourceUrl,
                author,
                tags,
                metrics,
                sha256Hex(title.toLowerCase() + "\n" + sourceUrl),
                rawItem.getPublishedAt()
        ));
    }

    private void addTag(ArrayNode tags, Set<String> seenTags, String value) {
        String normalized = cleanText(value);
        if (!normalized.isBlank() && seenTags.add(normalized.toLowerCase())) {
            tags.add(normalized);
        }
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/main/java/com/airadar/item/normalizer/SogouSearchHotItemNormalizer.java
git commit -m "feat: add SogouSearchHotItemNormalizer"
```

---

### Task 7: SourceConfigService Validation + application.yml

**Files:**
- Modify: `backend/src/main/java/com/airadar/source/service/SourceConfigService.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `SourceType.SOGOU_SEARCH` (Task 1)
- Produces: `validateSogouSearchConfig` method in `SourceConfigService`
- Produces: `ai-radar.collector.sogou-search` configuration section

- [ ] **Step 1: Add validation method to SourceConfigService**

In `SourceConfigService.java`, add `case SOGOU_SEARCH -> validateSogouSearchConfig(config);` to the `validateConfig` switch statement, and add the validation method:

```java
    private void validateSogouSearchConfig(JsonNode config) {
        String query = config.path("query").asText("").trim();
        if (query.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Sogou Search query must not be blank."
            );
        }
        int cnt = config.path("cnt").asInt(10);
        if (cnt != 10 && cnt != 20 && cnt != 30 && cnt != 40 && cnt != 50) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Sogou Search cnt must be one of 10, 20, 30, 40, 50."
            );
        }
        int mode = config.path("mode").asInt(0);
        if (mode != 0 && mode != 1 && mode != 2) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Sogou Search mode must be 0, 1, or 2."
            );
        }
        String freshness = config.path("freshness").asText("").trim();
        if (!freshness.isBlank() && !freshness.matches("^[dmy]\\d{0,2}$")) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Sogou Search freshness must match pattern d[N], m[N], y[N] (e.g. d1, d7, m3, y2) or be empty."
            );
        }
    }
```

The switch statement in `validateConfig` should become:

```java
    private void validateConfig(CreateSourceRequest request) {
        JsonNode config = objectMapper.valueToTree(request.config());
        switch (request.sourceType()) {
            case HACKER_NEWS -> validateHackerNewsConfig(config);
            case ARXIV -> validateArxivConfig(config);
            case GITHUB -> validateGitHubConfig(config);
            case HUGGING_FACE -> validateHuggingFaceConfig(config);
            case SOGOU_SEARCH -> validateSogouSearchConfig(config);
        }
    }
```

- [ ] **Step 2: Add sogou-search config to application.yml**

Add under `ai-radar.collector` (after the `hacker-news` block):

```yaml
    sogou-search:
      base-url: ${AI_RADAR_SOGOU_SEARCH_BASE_URL:https://wsa.tencentcloudapi.com}
      connect-timeout: ${AI_RADAR_SOGOU_SEARCH_CONNECT_TIMEOUT:3s}
      read-timeout: ${AI_RADAR_SOGOU_SEARCH_READ_TIMEOUT:8s}
      max-attempts: ${AI_RADAR_SOGOU_SEARCH_MAX_ATTEMPTS:2}
      secret-id: ${AI_RADAR_SOGOU_SEARCH_SECRET_ID:}
      secret-key: ${AI_RADAR_SOGOU_SEARCH_SECRET_KEY:}
```

- [ ] **Step 3: Verify compilation**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/main/java/com/airadar/source/service/SourceConfigService.java backend/src/main/resources/application.yml
git commit -m "feat: add SOGOU_SEARCH config validation and application.yml properties"
```

---

### Task 8: Raw Data Flow Integration Test

**Files:**
- Create: `backend/src/test/java/com/airadar/crawl/SogouSearchRawDataFlowIntegrationTest.java`

**Interfaces:**
- Consumes: All Tasks 1-7 (full closed loop)
- Proves: `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` for SOGOU_SEARCH

- [ ] **Step 1: Write the integration test**

Create `backend/src/test/java/com/airadar/crawl/SogouSearchRawDataFlowIntegrationTest.java`:

```java
package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.sogou.FetchedSogouSearchResult;
import com.airadar.crawl.client.sogou.SogouSearchClient;
import com.airadar.crawl.client.sogou.SogouSearchRequest;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.crawl.vo.CrawlTaskVO;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.model.SourceType;
import com.airadar.source.service.SourceConfigService;
import com.airadar.source.vo.SourceConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class SogouSearchRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private SogouSearchClient sogouSearchClient;

    @Autowired
    private SourceConfigService sourceConfigService;

    @Autowired
    private CrawlExecutionService crawlExecutionService;

    @Autowired
    private HotClusterQueryService hotClusterQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    crawl_task_error,
                    hot_score,
                    hot_cluster_item,
                    hot_cluster,
                    hot_item,
                    raw_item,
                    crawl_task,
                    source_config
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldNormalizeSogouSearchResultsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "sogou-ai-search",
                SourceType.SOGOU_SEARCH,
                "Sogou AI Search",
                true,
                null,
                Map.of(
                        "query", "大模型 OR 智能体",
                        "cnt", 20,
                        "mode", 0,
                        "site", "",
                        "freshness", "d1"
                )
        ));
        when(sogouSearchClient.search(any(SogouSearchRequest.class))).thenReturn(List.of(
                result("大模型发展报告", "https://example.com/article1", "大模型最新进展", "示例站", 0.95, 1),
                result("AI智能体应用", "https://example.com/article2", "智能体技术", "科技网", 0.80, 2)
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12a-sogou-raw-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(2);
        assertThat(task.persistedCount()).isEqualTo(2);
        assertThat(task.matchedCount()).isEqualTo(2);
        assertThat(task.failedCount()).isZero();
        assertThat(count("raw_item")).isEqualTo(2);
        assertThat(count("hot_item")).isEqualTo(2);
        assertThat(count("hot_cluster")).isEqualTo(2);

        String externalId = externalIdFor("https://example.com/article1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_type FROM raw_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("SOGOU_SEARCH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'query' FROM raw_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("大模型 OR 智能体");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("SEARCH_RESULT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("示例站");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'rank' FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("1");

        PageResponse<?> page = hotClusterQueryService.list(
                1, 20, HotClusterSort.SCORE_DESC, SourceType.SOGOU_SEARCH, null, null
        );
        assertThat(page.totalElements()).isEqualTo(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.SOGOU_SEARCH);

        verify(sogouSearchClient).search(any(SogouSearchRequest.class));
    }

    private FetchedSogouSearchResult result(String title, String url, String passage, String site, double score, int rank) {
        return new FetchedSogouSearchResult(
                title, url, passage, null, site, score, Instant.parse("2026-07-12T10:00:00Z"), rank
        );
    }

    private String externalIdFor(String url) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `cd D:\AiProgram\ai-radar\backend && .\mvnw.cmd test -Dtest=SogouSearchRawDataFlowIntegrationTest -q`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
cd D:\AiProgram\ai-radar
git add backend/src/test/java/com/airadar/crawl/SogouSearchRawDataFlowIntegrationTest.java
git commit -m "test: add Sogou search raw data flow integration test"
```

---

### Task 9: Frontend Changes

**Files:**
- Modify: `frontend/src/shared/api/contracts.ts`
- Modify: `frontend/src/shared/utils/query.ts`
- Modify: `frontend/src/shared/utils/query.test.ts`
- Modify: `frontend/src/pages/HotClusterListPage.vue`
- Modify: `frontend/src/pages/HotClusterDetailPage.vue`
- Modify: `frontend/src/pages/AlertsPage.vue`
- Modify: `frontend/src/pages/DailyReportsPage.vue`

**Interfaces:**
- Produces: `SOGOU_SEARCH` in `SourceType` union, `parseSourceType`, page labels, and filter options

- [ ] **Step 1: Add SOGOU_SEARCH to contracts.ts SourceType**

In `frontend/src/shared/api/contracts.ts`, change line 16:

```typescript
export type SourceType = "ARXIV" | "HACKER_NEWS" | "GITHUB" | "HUGGING_FACE" | "SOGOU_SEARCH";
```

- [ ] **Step 2: Add SOGOU_SEARCH to query.ts parseSourceType**

In `frontend/src/shared/utils/query.ts`, add `"SOGOU_SEARCH"` to `parseSourceType`:

```typescript
function parseSourceType(value: unknown): SourceType | undefined {
    const parsed = single(value);
    if (parsed === "ARXIV" || parsed === "HACKER_NEWS" || parsed === "GITHUB" || parsed === "HUGGING_FACE" || parsed === "SOGOU_SEARCH") {
        return parsed;
    }
    return undefined;
}
```

- [ ] **Step 3: Add SOGOU_SEARCH parse test to query.test.ts**

In `frontend/src/shared/utils/query.test.ts`, add a test inside the `describe` block:

```typescript
    it("parses SOGOU_SEARCH sourceType from query", () => {
        const filters = parseHotClusterFilters({ sourceType: "SOGOU_SEARCH" });
        expect(filters.sourceType).toBe("SOGOU_SEARCH");
    });
```

- [ ] **Step 4: Add SOGOU_SEARCH to HotClusterListPage.vue**

In `frontend/src/pages/HotClusterListPage.vue`:

Add option after the HUGGING_FACE option (around line 30):
```html
                <option value="SOGOU_SEARCH">搜狗搜索</option>
```

Update the `sourceTypeLabel` function (around line 348) to accept `SOGOU_SEARCH`:

```typescript
function sourceTypeLabel(sourceType: "ARXIV" | "HACKER_NEWS" | "GITHUB" | "HUGGING_FACE" | "SOGOU_SEARCH"): string {
  if (sourceType === "ARXIV") return "arXiv";
  if (sourceType === "GITHUB") return "GitHub";
  if (sourceType === "HUGGING_FACE") return "Hugging Face";
  if (sourceType === "SOGOU_SEARCH") return "搜狗搜索";
  return "Hacker News";
}
```

- [ ] **Step 5: Add SOGOU_SEARCH to HotClusterDetailPage.vue**

Find the `sourceTypeLabel` function in `HotClusterDetailPage.vue` and add before the `return "Hacker News"` line:

```typescript
  if (source === "SOGOU_SEARCH") return "搜狗搜索";
```

- [ ] **Step 6: Add SOGOU_SEARCH to AlertsPage.vue**

In `frontend/src/pages/AlertsPage.vue`:

Update `sourceOptions` array (around line 222):
```typescript
const sourceOptions: SourceType[] = ["HACKER_NEWS", "ARXIV", "GITHUB", "HUGGING_FACE", "SOGOU_SEARCH"];
```

Update the `sourceLabel` function (around line 396) to add before `return "Hacker News"`:
```typescript
  if (source === "SOGOU_SEARCH") return "搜狗搜索";
```

- [ ] **Step 7: Add SOGOU_SEARCH to DailyReportsPage.vue**

In `frontend/src/pages/DailyReportsPage.vue`, update the `sourceLabel` function (around line 239) to add before `return "Hacker News"`:

```typescript
  if (source === "SOGOU_SEARCH") return "搜狗搜索";
```

- [ ] **Step 8: Run frontend tests and build**

Run: `cd D:\AiProgram\ai-radar\frontend && npm test -- --run`
Expected: PASS

Run: `cd D:\AiProgram\ai-radar\frontend && npm run build`
Expected: Build completes without errors

- [ ] **Step 9: Commit**

```bash
cd D:\AiProgram\ai-radar
git add frontend/src/shared/api/contracts.ts frontend/src/shared/utils/query.ts frontend/src/shared/utils/query.test.ts frontend/src/pages/
git commit -m "feat: add SOGOU_SEARCH source type to frontend contracts and pages"
```

---

### Task 10: Documentation Sync + Acceptance Script

**Files:**
- Modify: `docs/roadmap.md`
- Modify: `docs/decision-log.md`
- Modify: `docs/project-context.md`
- Modify: `README.md`
- Create: `docs/phase12a-acceptance.md`
- Create: `scripts/accept-phase-12a.ps1`

- [ ] **Step 1: Update roadmap.md**

Add after the Phase 11B section:

```markdown
## Phase 12A: Sogou Search Source

**Status:** In Progress

### Goals

- add the first Chinese platform source via the Tencent Cloud Web Search API (wsa)
- reuse the existing `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` closed loop
- keep the integration offline-testable without real API credentials

### Deliverables

- `SOGOU_SEARCH` source type with Tencent Cloud v3 signature support
- `TencentCloudV3Signer` reusable utility for TC3-HMAC-SHA256 signature
- `SogouSearchClient`, `SogouSearchCollector`, and `SogouSearchHotItemNormalizer`
- source config validation for query, cnt, mode, and freshness
- raw-to-hot integration test proving the full closed loop
- `CRAWL_PROVIDER_NOT_CONFIGURED` error code for missing credentials
- frontend source label "搜狗搜索" and filter option
- Phase 12A acceptance script and acceptance note
- documentation sync: roadmap, decision log, project context, README
```

- [ ] **Step 2: Update decision-log.md**

Add decision #24 after decision #23:

```markdown
24. Phase 12A introduces the first Chinese platform source via the Tencent Cloud Web Search API (wsa, sourced from Sogou Search), not web scraping. The TC3-HMAC-SHA256 signature is implemented manually in a reusable `TencentCloudV3Signer` utility rather than introducing the Tencent Cloud Java SDK, keeping the dependency footprint unchanged. Config fields align with actual API parameters (`query`, `cnt`, `mode`, `site`, `freshness`). A new `CRAWL_PROVIDER_NOT_CONFIGURED` error code covers missing credentials without blocking application startup.
```

- [ ] **Step 3: Update project-context.md**

Add to the "MVP Sources" section after "Hugging Face Models":

```markdown
### Sogou Search

Purpose: monitor Chinese-language AI news and discussions via the Tencent Cloud Web Search API.

Phase 12A fields:

- `title`
- `url`
- `passage`
- `content` (premium only)
- `site`
- `score`
- `date`
- `rank`
- `query`
```

Add to "MVP Functional Modules" a new item 15:

```markdown
15. Sogou Search as the first Chinese platform source via Tencent Cloud Web Search API, with manual TC3-HMAC-SHA256 signature
```

- [ ] **Step 4: Update README.md**

Update the "Current Status" line to include Phase 12A:

```markdown
**Phase 1 completed / Phase 2 completed / Phase 3 completed / Phase 4 completed / Phase 5 completed / Phase 6 completed / Phase 7 completed / Phase 8 completed / Phase 9A completed / Phase 10 completed / Phase 11A in progress / Phase 11B in progress / Phase 12A in progress**
```

Add a paragraph after the Phase 11A/11B paragraph:

```markdown
Phase 12A adds the first Chinese platform source via the Tencent Cloud Web Search API (wsa, sourced from Sogou Search). The integration uses a manually implemented TC3-HMAC-SHA256 signer (`TencentCloudV3Signer`) rather than the Tencent Cloud SDK, keeping the dependency footprint unchanged. The application starts even when `AI_RADAR_SOGOU_SEARCH_SECRET_ID` or `AI_RADAR_SOGOU_SEARCH_SECRET_KEY` is missing; in that case crawl tasks fail with `CRAWL.PROVIDER_NOT_CONFIGURED`.
```

- [ ] **Step 5: Create acceptance script**

Create `scripts/accept-phase-12a.ps1`:

```powershell
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"

$checks = @(
    @{
        Name = "Backend Sogou Search tests"
        Command = ".\\mvnw.cmd"
        Args = @("-Dtest=TencentCloudV3SignerTest,SogouSearchClientTest,SogouSearchRawDataFlowIntegrationTest", "test")
        Workdir = $backendDir
    },
    @{
        Name = "Frontend tests"
        Command = "npm"
        Args = @("test", "--", "--run")
        Workdir = $frontendDir
    },
    @{
        Name = "Frontend build"
        Command = "npm"
        Args = @("run", "build")
        Workdir = $frontendDir
    }
)

foreach ($check in $checks) {
    Write-Host ""
    Write-Host "==> $($check.Name)" -ForegroundColor Cyan
    Write-Host "    cwd: $($check.Workdir)"
    Write-Host "    cmd: $($check.Command) $($check.Args -join ' ')"

    Push-Location $check.Workdir
    try {
        & $check.Command @($check.Args)
        if ($LASTEXITCODE -ne 0) {
            throw "$($check.Name) failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "Phase 12A acceptance passed." -ForegroundColor Green
Write-Host "Validated closed loop:"
Write-Host "  Sogou Search client -> collector -> raw_item -> hot_item -> hot_cluster"
Write-Host "  frontend compatibility -> source enum filters and production bundle"
```

- [ ] **Step 6: Create acceptance note**

Create `docs/phase12a-acceptance.md`:

```markdown
# Phase 12A Acceptance

**Date:** 2026-07-12
**Status:** Accepted

## Verified Capabilities

- `SOGOU_SEARCH` source type can be created via `SourceConfigService`
- Manual crawl task triggers `SogouSearchClient.search()` with TC3-HMAC-SHA256 signature
- `raw_item` persists with `source_type = SOGOU_SEARCH` and full search result payload
- `hot_item` normalizes to `item_type = SEARCH_RESULT` with rank-based points
- `hot_cluster` and `hot_score` are generated through the existing clustering and scoring pipeline
- Hot cluster API supports `sourceType=SOGOU_SEARCH` filtering
- Frontend displays "搜狗搜索" source label and filter option
- Application starts without `AI_RADAR_SOGOU_SEARCH_SECRET_ID` / `AI_RADAR_SOGOU_SEARCH_SECRET_KEY`
- `TencentCloudV3Signer` produces valid TC3-HMAC-SHA256 signatures
- `CRAWL_PROVIDER_NOT_CONFIGURED` error code covers missing credentials

## Test Coverage

- `TencentCloudV3SignerTest`: signature structure, determinism, payload sensitivity, credential scope
- `SogouSearchClientTest`: signed POST, Pages parsing, empty response, upstream error, missing fields
- `SogouSearchRawDataFlowIntegrationTest`: full `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` flow
- Frontend: `parseSourceType` includes `SOGOU_SEARCH`, production build passes

## Acceptance Script

```
cd D:\AiProgram\ai-radar
.\scripts\accept-phase-12a.ps1
```
```

- [ ] **Step 7: Run the acceptance script**

Run: `cd D:\AiProgram\ai-radar && powershell -ExecutionPolicy Bypass -File scripts\accept-phase-12a.ps1`
Expected: All 3 checks pass, "Phase 12A acceptance passed."

- [ ] **Step 8: Commit**

```bash
cd D:\AiProgram\ai-radar
git add docs/roadmap.md docs/decision-log.md docs/project-context.md README.md docs/phase12a-acceptance.md scripts/accept-phase-12a.ps1
git commit -m "docs: sync phase 12A documentation and add acceptance script"
```
