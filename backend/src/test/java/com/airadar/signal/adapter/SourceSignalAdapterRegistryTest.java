package com.airadar.signal.adapter;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceSignalAdapterRegistryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldRegisterAndAdaptSupportedSourceTypes() {
        SourceSignalAdapterRegistry registry = new SourceSignalAdapterRegistry(List.of(
            new HackerNewsSignalAdapter(),
            new GitHubSignalAdapter(),
            new HuggingFaceSignalAdapter(),
            new SearchSignalAdapter(),
            new DuckDuckGoSearchSignalAdapter()
        ));
        HotItemEntity item = new HotItemEntity();
        item.setSourceType(SourceType.BING_SEARCH);
        item.setMetrics(OBJECT_MAPPER.createObjectNode().put("rank", 2).put("totalCount", 10));

        NormalizedSignal signal = registry.adapt(item);

        assertThat(registry.size()).isEqualTo(5);
        assertThat(registry.hasAdapter(SourceType.HACKER_NEWS)).isTrue();
        assertThat(registry.hasAdapter(SourceType.BING_SEARCH)).isTrue();
        assertThat(registry.hasAdapter(SourceType.DUCKDUCKGO_SEARCH)).isTrue();
        assertThat(registry.hasAdapter(SourceType.SOGOU_SEARCH)).isFalse();
        assertThat(signal.sourceType()).isEqualTo(SourceType.BING_SEARCH);
        assertThat(signal.relevance()).isEqualTo(90.0);
    }

    @Test
    void shouldRejectMissingAdapterWithBusinessException() {
        SourceSignalAdapterRegistry registry = new SourceSignalAdapterRegistry(List.of(new SearchSignalAdapter()));

        assertThatThrownBy(() -> registry.getRequired(SourceType.SOGOU_SEARCH))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.SOURCE_TYPE_UNSUPPORTED);
    }

    @Test
    void shouldRejectDuplicateAdapters() {
        assertThatThrownBy(() -> new SourceSignalAdapterRegistry(List.of(
            new SearchSignalAdapter(),
            new DuplicateBingSearchSignalAdapter()
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate signal adapter for BING_SEARCH");
    }

    private static class DuplicateBingSearchSignalAdapter extends SearchSignalAdapter {
        @Override
        public SourceType supportedType() {
            return SourceType.BING_SEARCH;
        }
    }
}
