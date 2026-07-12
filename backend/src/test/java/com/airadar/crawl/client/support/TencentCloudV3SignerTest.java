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
