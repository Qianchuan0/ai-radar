package com.airadar.scoring.calculator;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceDiversityCalculatorTest {

    private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();
    private final EvidenceDiversityCalculator calculator = new EvidenceDiversityCalculator(canonicalizer);

    @Test
    void compute_countsDistinctRoles() {
        HotItemEntity github = item(1L, "https://github.com/x/y");
        HotItemEntity hn = item(2L, "https://news.ycombinator.com/item?id=1");
        Map<Long, NormalizedSignal> signals = Map.of(
                1L, signal(SourceRole.ADOPTION),
                2L, signal(SourceRole.COMMUNITY)
        );

        ScoreComponent result = calculator.compute(context(List.of(github, hn), signals));

        // 2 distinct roles * 25 = 50
        assertThat(result.score()).isEqualTo(50.0);
    }

    @Test
    void compute_dedupesSearchSourcesWithSameUrl() {
        // Three search engines pointing at the same canonical URL count as one DISCOVERY
        String sharedUrl = "https://example.com/article";
        HotItemEntity bing = item(1L, sharedUrl);
        HotItemEntity duck = item(2L, sharedUrl);
        HotItemEntity sogou = item(3L, sharedUrl);
        Map<Long, NormalizedSignal> signals = Map.of(
                1L, signal(SourceRole.DISCOVERY),
                2L, signal(SourceRole.DISCOVERY),
                3L, signal(SourceRole.DISCOVERY)
        );

        ScoreComponent result = calculator.compute(context(List.of(bing, duck, sogou), signals));

        // Only one DISCOVERY role despite three sources
        assertThat(result.score()).isEqualTo(25.0);
        assertThat(result.reasons()).anyMatch(r -> r.contains("deduped_search_urls=1"));
    }

    @Test
    void compute_treatsDifferentUrlsAsSeparateDiscovery() {
        HotItemEntity bing = item(1L, "https://example.com/a");
        HotItemEntity duck = item(2L, "https://example.com/b");
        Map<Long, NormalizedSignal> signals = Map.of(
                1L, signal(SourceRole.DISCOVERY),
                2L, signal(SourceRole.DISCOVERY)
        );

        ScoreComponent result = calculator.compute(context(List.of(bing, duck), signals));

        // Two distinct URLs -> still one DISCOVERY role, so 25
        assertThat(result.score()).isEqualTo(25.0);
    }

    @Test
    void compute_mergesTrackingParametersBeforeDedup() {
        // utm/ref tracking params are stripped by UrlCanonicalizer, so these dedupe
        HotItemEntity bing = item(1L, "https://example.com/article?utm_source=bing");
        HotItemEntity duck = item(2L, "https://example.com/article?ref=duck");
        Map<Long, NormalizedSignal> signals = Map.of(
                1L, signal(SourceRole.DISCOVERY),
                2L, signal(SourceRole.DISCOVERY)
        );

        ScoreComponent result = calculator.compute(context(List.of(bing, duck), signals));

        assertThat(result.score()).isEqualTo(25.0);
        assertThat(result.reasons()).anyMatch(r -> r.contains("deduped_search_urls=1"));
    }

    private HotItemEntity item(long id, String url) {
        HotItemEntity item = new HotItemEntity();
        item.setId(id);
        item.setSourceUrl(url);
        return item;
    }

    private NormalizedSignal signal(SourceRole role) {
        return new NormalizedSignal(SourceType.BING_SEARCH, role, 0, 0, 0, 0, 0, null, null);
    }

    private ScoringContext context(List<HotItemEntity> items, Map<Long, NormalizedSignal> signals) {
        return new ScoringContext(
                new HotClusterEntity(),
                items,
                items.isEmpty() ? null : items.get(0),
                signals,
                Map.of(),
                Instant.parse("2026-07-17T12:00:00Z")
        );
    }
}
