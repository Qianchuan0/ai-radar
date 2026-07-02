package com.airadar.cluster.support;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class UrlCanonicalizer {

    public String canonicalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            if (host == null) {
                return value.trim();
            }
            int port = normalizePort(scheme, uri.getPort());
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            String query = normalizeQuery(uri.getRawQuery());
            return new URI(scheme, uri.getUserInfo(), host, port, path, query, null).toString();
        } catch (URISyntaxException exception) {
            return value.trim();
        }
    }

    private int normalizePort(String scheme, int port) {
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        String normalized = Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> !isTrackingParameter(decode(parts[0])))
                .sorted(Comparator.comparing(parts -> decode(parts[0])))
                .map(parts -> encode(decode(parts[0]))
                        + (parts.length == 2 ? "=" + encode(decode(parts[1])) : ""))
                .collect(Collectors.joining("&"));
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isTrackingParameter(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.startsWith("utm_")
                || normalized.equals("ref")
                || normalized.equals("source")
                || normalized.equals("fbclid")
                || normalized.equals("gclid");
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
