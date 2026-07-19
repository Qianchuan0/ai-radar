package com.airadar.evaluation.cluster;

import com.airadar.source.model.SourceType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Frozen default fixture for the Phase 16A clustering evaluation baseline.
 *
 * <p>The fixture intentionally covers two distinct difficulty tiers:
 * <ul>
 *   <li><b>URL-shared must-merge cases</b> (group 1) — both items share a
 *       canonical URL. {@code hn-rule-v1} should already merge these; this
 *       is the V1 baseline that must not regress under V2.</li>
 *   <li><b>Cross-URL must-merge cases</b> (groups 2-5) — the same event is
 *       described by different URLs, sources, and phrasings.
 *       {@code hn-rule-v1} cannot merge these today; {@code event-rule-v2}
 *       is expected to merge them via entity + event-type signals.</li>
 * </ul>
 *
 * <p>The must-not-merge pairs cover the canonical false-merge risks: same
 * product but different event type (release vs pricing, release vs security
 * disclosure, release vs patch), and different products that happen to share
 * a market context.
 *
 * <p><b>Frozen contract:</b> once this fixture is referenced by an acceptance
 * script or a stored report, it must not be edited. Ship a new fixture with a
 * new version tag (e.g. {@code phase16-baseline-v2}) so historical reports
 * remain interpretable.
 */
public final class ClusterBaselineFixtures {

    public static final String DEFAULT_VERSION = "phase16a-baseline-v1";

    private static final Instant FIXED_PUBLISHED_AT = Instant.parse("2099-01-01T00:00:00Z");

    private ClusterBaselineFixtures() {
    }

    public static ClusterBaselineFixture defaultFixture() {
        List<FixtureInputItem> items = List.of(
                // --- OpenAI GPT-5 launch event (one event, four sources) ---
                blogPost("openai-gpt5-blog",
                        "https://openai.com/blog/gpt-5",
                        "OpenAI launches GPT-5 with major reasoning upgrades",
                        "openai-blog"),
                hnStory("openai-gpt5-hn",
                        "https://openai.com/blog/gpt-5",
                        "OpenAI launches GPT-5",
                        "openai-hn"),
                tweet("openai-gpt5-tweet",
                        "https://twitter.com/openai/status/1001",
                        "We are excited to announce GPT-5, our most capable model yet",
                        "OpenAI"),
                mediaArticle("openai-gpt5-techcrunch",
                        "https://techcrunch.com/2026/07/openai-gpt5-launch",
                        "OpenAI's GPT-5 launch reshapes the AI model landscape",
                        "TechCrunch"),

                // --- OpenAI GPT-5 pricing event (different event, same product) ---
                hnStory("openai-gpt5-pricing",
                        "https://openai.com/pricing",
                        "OpenAI adjusts GPT-5 API pricing",
                        "openai-hn"),

                // --- Anthropic Claude Code events ---
                blogPost("anthropic-claude-release",
                        "https://www.anthropic.com/news/claude-code-2",
                        "Anthropic launches Claude Code 2.0",
                        "anthropic-blog"),
                blogPost("anthropic-claude-vuln",
                        "https://www.anthropic.com/news/claude-code-vuln",
                        "Security vulnerability disclosed in Claude Code",
                        "anthropic-blog"),

                // --- Meta Llama 3 events ---
                blogPost("meta-llama3-release",
                        "https://ai.meta.com/blog/llama-3-1",
                        "Meta releases Llama 3.1 405B open weights",
                        "meta-blog"),
                blogPost("meta-llama3-update",
                        "https://ai.meta.com/blog/llama-3-1-patch",
                        "Meta ships Llama 3.1 patch for tokenizer issue",
                        "meta-blog"),

                // --- arXiv paper cross-source (URL differs but arXiv id shared) ---
                arxivPaper("arxiv-2401-00123",
                        "https://arxiv.org/abs/2401.00123",
                        "Scaling Laws for Multimodal Models"),
                hnStory("arxiv-2401-00123-hn",
                        "https://news.ycombinator.com/item?id=99999",
                        "Discussion: Scaling Laws for Multimodal Models (arXiv 2401.00123)",
                        "arxiv-fan"),

                // --- Hugging Face model + media coverage (different URL, same model) ---
                hfModel("hf-openai-gpt5",
                        "https://huggingface.co/openai/gpt-5",
                        "openai/gpt-5"),
                mediaArticle("hf-openai-gpt5-media",
                        "https://techcrunch.com/2026/07/openai-hf-gpt5",
                        "OpenAI publishes GPT-5 weights on Hugging Face",
                        "TechCrunch")
        );

        List<Set<String>> mustMerge = List.of(
                Set.of("openai-gpt5-blog", "openai-gpt5-hn"),
                Set.of("openai-gpt5-blog", "openai-gpt5-tweet"),
                Set.of("openai-gpt5-blog", "openai-gpt5-techcrunch"),
                Set.of("arxiv-2401-00123", "arxiv-2401-00123-hn"),
                Set.of("hf-openai-gpt5", "hf-openai-gpt5-media")
        );

        List<ClusterBaselineFixture.MustNotMergePair> mustNotMerge = List.of(
                new ClusterBaselineFixture.MustNotMergePair("openai-gpt5-blog", "openai-gpt5-pricing"),
                new ClusterBaselineFixture.MustNotMergePair("anthropic-claude-release", "anthropic-claude-vuln"),
                new ClusterBaselineFixture.MustNotMergePair("meta-llama3-release", "meta-llama3-update"),
                new ClusterBaselineFixture.MustNotMergePair("openai-gpt5-blog", "anthropic-claude-release"),
                new ClusterBaselineFixture.MustNotMergePair("openai-gpt5-blog", "meta-llama3-release")
        );

        return new ClusterBaselineFixture(
                DEFAULT_VERSION,
                "Phase 16A default baseline: 13 items, 5 must-merge groups, 5 must-not-merge pairs",
                items,
                mustMerge,
                mustNotMerge
        );
    }

    private static FixtureInputItem blogPost(String key, String url, String title, String author) {
        return baseItem(key, url, title, SourceType.HACKER_NEWS, "STORY", author, pointsAndComments(150, 35));
    }

    private static FixtureInputItem hnStory(String key, String url, String title, String author) {
        return baseItem(key, url, title, SourceType.HACKER_NEWS, "STORY", author, pointsAndComments(120, 40));
    }

    private static FixtureInputItem tweet(String key, String url, String title, String author) {
        return FixtureInputItem.builder()
                .key(key)
                .externalId(key)
                .sourceType(SourceType.TWITTER)
                .itemType("TWEET")
                .title(title)
                .summary(title)
                .sourceUrl(url)
                .author(author)
                .tags(List.of("AI"))
                .metrics(tweetMetrics(5000, 800, 200))
                .publishedAt(FIXED_PUBLISHED_AT)
                .build();
    }

    private static FixtureInputItem mediaArticle(String key, String url, String title, String publisher) {
        return FixtureInputItem.builder()
                .key(key)
                .externalId(key)
                .sourceType(SourceType.BING_SEARCH)
                .itemType("WEB_PAGE")
                .title(title)
                .summary(title)
                .sourceUrl(url)
                .author(publisher)
                .tags(List.of("AI"))
                .metrics(searchRank(1))
                .publishedAt(FIXED_PUBLISHED_AT)
                .build();
    }

    private static FixtureInputItem arxivPaper(String key, String url, String title) {
        return FixtureInputItem.builder()
                .key(key)
                .externalId("2401.00123")
                .sourceType(SourceType.ARXIV)
                .itemType("PAPER")
                .title(title)
                .summary(title)
                .sourceUrl(url)
                .author("paper-authors")
                .tags(List.of("cs.LG"))
                .metrics(Map.of("authorsCount", 4, "categoriesCount", 2))
                .publishedAt(FIXED_PUBLISHED_AT)
                .build();
    }

    private static FixtureInputItem hfModel(String key, String url, String modelId) {
        return FixtureInputItem.builder()
                .key(key)
                .externalId(modelId)
                .sourceType(SourceType.HUGGING_FACE)
                .itemType("MODEL")
                .title(modelId)
                .summary(modelId)
                .sourceUrl(url)
                .author("openai")
                .tags(List.of("text-generation"))
                .metrics(Map.of("downloads", 50000, "likes", 300))
                .publishedAt(FIXED_PUBLISHED_AT)
                .build();
    }

    private static FixtureInputItem baseItem(String key, String url, String title, SourceType sourceType, String itemType, String author, Map<String, Object> metrics) {
        return FixtureInputItem.builder()
                .key(key)
                .externalId(key)
                .sourceType(sourceType)
                .itemType(itemType)
                .title(title)
                .summary(title)
                .sourceUrl(url)
                .author(author)
                .tags(List.of("AI"))
                .metrics(metrics)
                .publishedAt(FIXED_PUBLISHED_AT)
                .build();
    }

    private static Map<String, Object> pointsAndComments(int points, int comments) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("points", points);
        metrics.put("commentsCount", comments);
        return metrics;
    }

    private static Map<String, Object> tweetMetrics(int likes, int retweets, int replies) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("likeCount", likes);
        metrics.put("retweetCount", retweets);
        metrics.put("replyCount", replies);
        return metrics;
    }

    private static Map<String, Object> searchRank(int rank) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rank", rank);
        metrics.put("totalCount", 50);
        return metrics;
    }
}
