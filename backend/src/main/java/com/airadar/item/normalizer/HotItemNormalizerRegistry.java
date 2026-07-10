package com.airadar.item.normalizer;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class HotItemNormalizerRegistry {

    private final Map<SourceType, HotItemNormalizer> normalizers;

    public HotItemNormalizerRegistry(List<HotItemNormalizer> normalizerList) {
        Map<SourceType, HotItemNormalizer> registered = new EnumMap<>(SourceType.class);
        for (HotItemNormalizer normalizer : normalizerList) {
            HotItemNormalizer previous = registered.put(normalizer.supportedType(), normalizer);
            if (previous != null) {
                throw new IllegalStateException("Duplicate hot item normalizer for " + normalizer.supportedType());
            }
        }
        this.normalizers = Map.copyOf(registered);
    }

    public HotItemNormalizer getRequired(SourceType sourceType) {
        HotItemNormalizer normalizer = normalizers.get(sourceType);
        if (normalizer == null) {
            throw new BusinessException(ErrorCode.SOURCE_TYPE_UNSUPPORTED);
        }
        return normalizer;
    }
}
