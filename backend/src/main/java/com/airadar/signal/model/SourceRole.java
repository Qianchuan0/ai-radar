package com.airadar.signal.model;

import com.airadar.source.model.SourceType;

/**
 * Signal role classification for data sources.
 *
 * <p>Defines the semantic role of a source in the AI intelligence ecosystem,
 * distinguishing between primary research artifacts, adoption signals,
 * community discussions, discovery channels, and media coverage.
 */
public enum SourceRole {
    /**
     * Primary research artifacts and authoritative sources.
     *
     * <p>Examples: arXiv papers, official documentation, standards bodies.
     * Future: ARXIV → PRIMARY
     */
    PRIMARY,

    /**
     * Adoption and usage signals from developer platforms.
     *
     * <p>Indicates real-world adoption through stars, downloads, forks, or usage counts.
     * Examples: GitHub repositories, HuggingFace models.
     * Current: GITHUB, HUGGING_FACE → ADOPTION
     */
    ADOPTION,

    /**
     * Community discussion and social engagement.
     *
     * <p>Indicates public interest and discussion activity.
     * Examples: HackerNews, Weibo Hot Search, Twitter.
     * Current: HACKER_NEWS, WEIBO_HOT_SEARCH, HACKER_NEWS_SEARCH, TWITTER → COMMUNITY
     * Future: Twitter official accounts may be elevated to PRIMARY
     */
    COMMUNITY,

    /**
     * Media coverage and news articles.
     *
     * <p>Journalistic and editorial content about AI trends.
     * Future: TechCrunch, VentureBeat, AI-specific media outlets
     */
    MEDIA,

    /**
     * Web discovery and search engine results.
     *
     * <p>General web search results that lead to AI-related content.
     * Search sources contribute relevance and ranking signals but not social heat.
     * Examples: Bing, DuckDuckGo, Google, Sogou.
     * Current: BING_SEARCH, DUCKDUCKGO_SEARCH, SOGOU_SEARCH → DISCOVERY
     */
    DISCOVERY;

    /**
     * Maps a {@link SourceType} to its {@link SourceRole}.
     *
     * <p>This is the single source of truth for the SourceType → SourceRole mapping
     * used by both {@code SourceSignalAdapter} implementations and cluster-level
     * services (Phase 18A) that need to classify items without going through the
     * adapter registry.
     *
     * @param sourceType the source type to classify; {@code null} returns {@code PRIMARY}
     * @return the semantic role of the source type
     */
    public static SourceRole fromSourceType(SourceType sourceType) {
        if (sourceType == null) {
            return PRIMARY;
        }
        return switch (sourceType) {
            case GITHUB, HUGGING_FACE -> ADOPTION;
            case HACKER_NEWS, HACKER_NEWS_SEARCH, WEIBO_HOT_SEARCH, TWITTER -> COMMUNITY;
            case BING_SEARCH, DUCKDUCKGO_SEARCH, SOGOU_SEARCH -> DISCOVERY;
            case ARXIV -> PRIMARY;
        };
    }
}
