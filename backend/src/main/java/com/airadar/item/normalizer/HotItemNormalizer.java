package com.airadar.item.normalizer;

import com.airadar.item.model.NormalizedHotItem;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;

import java.util.Optional;

public interface HotItemNormalizer {

    SourceType supportedType();

    Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig);
}
