package com.airadar.cluster.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlCanonicalizerTest {

    private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();

    @Test
    void shouldRemoveTrackingParametersAndNormalizeUrl() {
        String actual = canonicalizer.canonicalize(
                "HTTPS://Example.COM:443/agent?utm_source=hn&b=2&a=1#section"
        );

        assertThat(actual).isEqualTo("https://example.com/agent?a=1&b=2");
    }
}
