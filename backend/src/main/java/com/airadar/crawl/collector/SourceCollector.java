package com.airadar.crawl.collector;

import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;

public interface SourceCollector {

    SourceType supportedType();

    CollectionBatch collect(SourceConfigEntity sourceConfig);
}
