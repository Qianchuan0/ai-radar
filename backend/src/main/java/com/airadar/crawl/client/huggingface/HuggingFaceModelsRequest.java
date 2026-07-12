package com.airadar.crawl.client.huggingface;

public record HuggingFaceModelsRequest(
        String search,
        String sort,
        String direction,
        int limit,
        String pipelineTag
) {
}
