package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceSignalAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void hackerNewsAdapterShouldMapCommunitySignals() {
        HotItemEntity item = hotItem(
            SourceType.HACKER_NEWS,
            metrics("points", 120, "commentsCount", 35)
        );

        NormalizedSignal signal = new HackerNewsSignalAdapter().adapt(item);

        assertThat(signal.sourceType()).isEqualTo(SourceType.HACKER_NEWS);
        assertThat(signal.sourceRole()).isEqualTo(SourceRole.COMMUNITY);
        assertThat(signal.attention()).isGreaterThan(0.0);
        assertThat(signal.discussion()).isGreaterThan(0.0);
        assertThat(signal.adoption()).isZero();
        assertThat(signal.totalSocialSignal()).isGreaterThan(0.0);
        assertThat(signal.rawMetrics()).isSameAs(item.getMetrics());
    }

    @Test
    void githubAdapterShouldMapAdoptionSignals() {
        HotItemEntity item = hotItem(
            SourceType.GITHUB,
            metrics("stargazersCount", 4200, "forksCount", 310, "watchersCount", 80, "openIssuesCount", 21)
        );

        NormalizedSignal signal = new GitHubSignalAdapter().adapt(item);

        assertThat(signal.sourceType()).isEqualTo(SourceType.GITHUB);
        assertThat(signal.sourceRole()).isEqualTo(SourceRole.ADOPTION);
        assertThat(signal.attention()).isGreaterThan(0.0);
        assertThat(signal.discussion()).isGreaterThan(0.0);
        assertThat(signal.adoption()).isGreaterThan(signal.discussion());
        assertThat(signal.rawMetrics()).isSameAs(item.getMetrics());
    }

    @Test
    void huggingFaceAdapterShouldMapDownloadAdoptionSignals() {
        HotItemEntity item = hotItem(
            SourceType.HUGGING_FACE,
            metrics("downloads", 12_500, "likes", 180)
        );

        NormalizedSignal signal = new HuggingFaceSignalAdapter().adapt(item);

        assertThat(signal.sourceType()).isEqualTo(SourceType.HUGGING_FACE);
        assertThat(signal.sourceRole()).isEqualTo(SourceRole.ADOPTION);
        assertThat(signal.attention()).isGreaterThan(0.0);
        assertThat(signal.discussion()).isZero();
        assertThat(signal.adoption()).isGreaterThan(signal.attention());
    }

    @Test
    void bingSearchAdapterShouldMapRelevanceWithoutSocialHeat() {
        HotItemEntity item = hotItem(
            SourceType.BING_SEARCH,
            metrics("rank", 3, "totalCount", 10)
        );

        NormalizedSignal signal = new SearchSignalAdapter().adapt(item);

        assertThat(signal.sourceType()).isEqualTo(SourceType.BING_SEARCH);
        assertThat(signal.sourceRole()).isEqualTo(SourceRole.DISCOVERY);
        assertThat(signal.relevance()).isEqualTo(80.0);
        assertThat(signal.getRank()).contains(3);
        assertThat(signal.isSearchResult()).isTrue();
        assertThat(signal.totalSocialSignal()).isZero();
        assertThat(signal.rawMetrics()).isSameAs(item.getMetrics());
    }

    @Test
    void duckDuckGoSearchAdapterShouldDeclareDuckDuckGoSourceType() {
        HotItemEntity item = hotItem(
            SourceType.DUCKDUCKGO_SEARCH,
            metrics("rank", 1, "totalCount", 10)
        );

        SourceSignalAdapter adapter = new DuckDuckGoSearchSignalAdapter();
        NormalizedSignal signal = adapter.adapt(item);

        assertThat(adapter.supportedType()).isEqualTo(SourceType.DUCKDUCKGO_SEARCH);
        assertThat(signal.sourceType()).isEqualTo(SourceType.DUCKDUCKGO_SEARCH);
        assertThat(signal.sourceRole()).isEqualTo(SourceRole.DISCOVERY);
        assertThat(signal.relevance()).isEqualTo(100.0);
        assertThat(signal.totalSocialSignal()).isZero();
    }

    @Test
    void searchAdapterShouldHandleZeroTotalCountWithoutInfiniteRelevance() {
        HotItemEntity item = hotItem(
            SourceType.BING_SEARCH,
            metrics("rank", 1, "totalCount", 0)
        );

        NormalizedSignal signal = new SearchSignalAdapter().adapt(item);

        assertThat(signal.relevance()).isZero();
        assertThat(signal.totalSocialSignal()).isZero();
        assertThat(Double.isFinite(signal.relevance())).isTrue();
    }

    @Test
    void adaptersShouldReturnZeroSignalsForMissingMetrics() {
        assertThat(new HackerNewsSignalAdapter().adapt(hotItem(SourceType.HACKER_NEWS, null)).totalSocialSignal())
            .isZero();
        assertThat(new GitHubSignalAdapter().adapt(hotItem(SourceType.GITHUB, null)).totalSocialSignal())
            .isZero();
        assertThat(new HuggingFaceSignalAdapter().adapt(hotItem(SourceType.HUGGING_FACE, null)).totalSocialSignal())
            .isZero();

        NormalizedSignal searchSignal = new SearchSignalAdapter().adapt(hotItem(SourceType.BING_SEARCH, null));
        assertThat(searchSignal.relevance()).isZero();
        assertThat(searchSignal.totalSocialSignal()).isZero();
    }

    private static HotItemEntity hotItem(SourceType sourceType, JsonNode metrics) {
        HotItemEntity item = new HotItemEntity();
        item.setSourceType(sourceType);
        item.setMetrics(metrics);
        return item;
    }

    private static JsonNode metrics(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must contain key/value pairs");
        }
        var node = OBJECT_MAPPER.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.put((String) keyValues[i], (Integer) keyValues[i + 1]);
        }
        return node;
    }
}
