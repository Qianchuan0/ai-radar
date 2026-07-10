package com.airadar.crawl.client.arxiv;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArxivClient {

    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final String ARXIV_NAMESPACE = "http://arxiv.org/schemas/atom";

    private final RestClient restClient;
    private final int maxAttempts;
    private final Duration minRequestInterval;
    private final Object requestLock = new Object();
    private Instant lastRequestAt;

    public ArxivClient(RestClient.Builder restClientBuilder, ArxivProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.maxAttempts = properties.maxAttempts();
        this.minRequestInterval = properties.minRequestInterval();
    }

    public List<FetchedArxivPaper> search(ArxivSearchRequest request) {
        String responseBody = executeWithRetry(() -> {
            waitForRequestWindow();
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("search_query", request.searchQuery())
                            .queryParam("start", request.start())
                            .queryParam("max_results", request.maxResults())
                            .queryParam("sortBy", request.sortBy().apiValue())
                            .queryParam("sortOrder", request.sortOrder().apiValue())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                        throw new BusinessException(
                                ErrorCode.CRAWL_UPSTREAM_ERROR,
                                "arXiv returned " + response.getStatusCode()
                        );
                    })
                    .body(String.class);
        });
        return parseResponse(responseBody);
    }

    private List<FetchedArxivPaper> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        Document document = parseXml(responseBody);
        NodeList entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry");
        List<FetchedArxivPaper> papers = new ArrayList<>(entries.getLength());
        for (int index = 0; index < entries.getLength(); index++) {
            Element entry = (Element) entries.item(index);
            papers.add(new FetchedArxivPaper(
                    extractArxivId(entry),
                    requiredText(entry, ATOM_NAMESPACE, "title"),
                    normalizeWhitespace(requiredText(entry, ATOM_NAMESPACE, "summary")),
                    extractRepeatedText(entry, ATOM_NAMESPACE, "author", ATOM_NAMESPACE, "name"),
                    extractCategoryTerms(entry),
                    Instant.parse(requiredText(entry, ATOM_NAMESPACE, "published")),
                    findPdfUrl(entry),
                    requiredText(entry, ATOM_NAMESPACE, "id")
            ));
        }
        return List.copyOf(papers);
    }

    private Document parseXml(String responseBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new SilentFailingErrorHandler());
            return builder.parse(new InputSource(new StringReader(responseBody)));
        } catch (ParserConfigurationException | IOException | SAXException exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid arXiv Atom XML response.");
        }
    }

    private String extractArxivId(Element entry) {
        String arxivId = firstText(entry, ARXIV_NAMESPACE, "id");
        if (arxivId != null && !arxivId.isBlank()) {
            return arxivId;
        }
        String sourceUrl = requiredText(entry, ATOM_NAMESPACE, "id");
        int versionSeparator = sourceUrl.lastIndexOf("/abs/");
        if (versionSeparator >= 0) {
            return sourceUrl.substring(versionSeparator + 5);
        }
        return sourceUrl;
    }

    private List<String> extractCategoryTerms(Element entry) {
        NodeList categories = entry.getElementsByTagNameNS(ATOM_NAMESPACE, "category");
        List<String> terms = new ArrayList<>(categories.getLength());
        for (int index = 0; index < categories.getLength(); index++) {
            Node node = categories.item(index);
            if (node instanceof Element element) {
                String term = element.getAttribute("term");
                if (!term.isBlank()) {
                    terms.add(term);
                }
            }
        }
        return List.copyOf(terms);
    }

    private List<String> extractRepeatedText(
            Element parent,
            String itemNamespace,
            String itemTag,
            String valueNamespace,
            String valueTag
    ) {
        NodeList nodes = parent.getElementsByTagNameNS(itemNamespace, itemTag);
        List<String> values = new ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) {
                String value = firstText(element, valueNamespace, valueTag);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private String findPdfUrl(Element entry) {
        NodeList links = entry.getElementsByTagNameNS(ATOM_NAMESPACE, "link");
        for (int index = 0; index < links.getLength(); index++) {
            Node node = links.item(index);
            if (node instanceof Element element) {
                if ("pdf".equals(element.getAttribute("title"))) {
                    String href = element.getAttribute("href");
                    if (!href.isBlank()) {
                        return href;
                    }
                }
            }
        }
        return null;
    }

    private String requiredText(Element parent, String namespace, String localName) {
        String value = firstText(parent, namespace, localName);
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                    "arXiv entry is missing required field: " + localName
            );
        }
        return value;
    }

    private String firstText(Element parent, String namespace, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null ? null : normalizeWhitespace(value);
    }

    private String normalizeWhitespace(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private <T> T executeWithRetry(UpstreamCall<T> call) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.execute();
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

    private void waitForRequestWindow() {
        synchronized (requestLock) {
            if (lastRequestAt != null && !minRequestInterval.isZero()) {
                Duration elapsed = Duration.between(lastRequestAt, Instant.now());
                Duration waitTime = minRequestInterval.minus(elapsed);
                if (!waitTime.isNegative() && !waitTime.isZero()) {
                    try {
                        Thread.sleep(waitTime.toMillis());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "arXiv request was interrupted.");
                    }
                }
            }
            lastRequestAt = Instant.now();
        }
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "arXiv request was interrupted.");
        }
    }

    @FunctionalInterface
    private interface UpstreamCall<T> {
        T execute();
    }

    private static final class SilentFailingErrorHandler implements org.xml.sax.ErrorHandler {

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
