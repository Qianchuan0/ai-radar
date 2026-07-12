package com.airadar.crawl.client.huggingface;

import java.time.Instant;
import java.util.List;

public record FetchedHuggingFaceModel(
        String modelId,
        String id,
        int downloads,
        int likes,
        List<String> tags,
        String pipelineTag,
        String author,
        String libraryName,
        Instant createdAt,
        Instant lastModified,
        boolean privateModel
) {
}
