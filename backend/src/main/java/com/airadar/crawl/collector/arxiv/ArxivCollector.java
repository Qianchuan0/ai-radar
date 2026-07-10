package com.airadar.crawl.collector.arxiv;

import com.airadar.crawl.client.arxiv.ArxivClient;
import com.airadar.crawl.client.arxiv.ArxivSearchRequest;
import com.airadar.crawl.client.arxiv.ArxivSortBy;
import com.airadar.crawl.client.arxiv.ArxivSortOrder;
import com.airadar.crawl.client.arxiv.FetchedArxivPaper;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.SourceCollector;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ArxivCollector implements SourceCollector {

    private static final int DEFAULT_MAX_RESULTS = 20;

    private final ArxivClient arxivClient;
    private final ObjectMapper objectMapper;

    public ArxivCollector(ArxivClient arxivClient, ObjectMapper objectMapper) {
        this.arxivClient = arxivClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.ARXIV;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        List<FetchedArxivPaper> papers = arxivClient.search(new ArxivSearchRequest(
                config.path("searchQuery").asText(),
                config.path("start").asInt(0),
                config.path("maxResults").asInt(DEFAULT_MAX_RESULTS),
                resolveSortBy(config.path("sortBy").asText(null)),
                resolveSortOrder(config.path("sortOrder").asText(null))
        ));
        return new CollectionBatch(papers.stream().map(this::toCollectedItem).toList(), List.of());
    }

    private CollectedItem toCollectedItem(FetchedArxivPaper paper) {
        return new CollectedItem(
                paper.arxivId(),
                paper.sourceUrl(),
                toRawPayload(paper),
                paper.publishedAt(),
                Instant.now()
        );
    }

    private JsonNode toRawPayload(FetchedArxivPaper paper) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("arxivId", paper.arxivId());
        payload.put("title", paper.title());
        payload.put("summary", paper.summary());
        payload.put("publishedAt", paper.publishedAt() == null ? null : paper.publishedAt().toString());
        payload.put("pdfUrl", paper.pdfUrl());
        payload.put("sourceUrl", paper.sourceUrl());

        ArrayNode authors = objectMapper.createArrayNode();
        paper.authors().forEach(authors::add);
        payload.set("authors", authors);

        ArrayNode categories = objectMapper.createArrayNode();
        paper.categories().forEach(categories::add);
        payload.set("categories", categories);
        return payload;
    }

    private ArxivSortBy resolveSortBy(String value) {
        if (value == null || value.isBlank()) {
            return ArxivSortBy.SUBMITTED_DATE;
        }
        return switch (value.trim().toUpperCase()) {
            case "RELEVANCE" -> ArxivSortBy.RELEVANCE;
            case "LAST_UPDATED_DATE" -> ArxivSortBy.LAST_UPDATED_DATE;
            case "SUBMITTED_DATE" -> ArxivSortBy.SUBMITTED_DATE;
            default -> throw new IllegalArgumentException("Unsupported arXiv sortBy: " + value);
        };
    }

    private ArxivSortOrder resolveSortOrder(String value) {
        if (value == null || value.isBlank()) {
            return ArxivSortOrder.DESCENDING;
        }
        return switch (value.trim().toUpperCase()) {
            case "ASC", "ASCENDING" -> ArxivSortOrder.ASCENDING;
            case "DESC", "DESCENDING" -> ArxivSortOrder.DESCENDING;
            default -> throw new IllegalArgumentException("Unsupported arXiv sortOrder: " + value);
        };
    }
}
