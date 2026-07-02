package com.airadar.crawl.collector;

import java.util.List;

public record CollectionBatch(
        List<CollectedItem> items,
        List<CollectionError> errors
) {

    public CollectionBatch {
        items = List.copyOf(items);
        errors = List.copyOf(errors);
    }
}
