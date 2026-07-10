package com.airadar.crawl.client.arxiv;

import java.time.Instant;
import java.util.List;

public record FetchedArxivPaper(
        String arxivId,
        String title,
        String summary,
        List<String> authors,
        List<String> categories,
        Instant publishedAt,
        String pdfUrl,
        String sourceUrl
) {
}
