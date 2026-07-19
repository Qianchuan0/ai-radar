package com.airadar.cluster.feature.extractor;

import com.airadar.cluster.support.UrlCanonicalizer;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts typed external identifiers from a hot item's source URL and
 * external id.
 *
 * <p>Level 1 matching treats the following as deterministic identifiers
 * (any one is sufficient to ACCEPT):
 * <ul>
 *   <li>{@code arxiv} — arXiv paper id, e.g. {@code 2401.00123}</li>
 *   <li>{@code github} — {@code owner/repo}</li>
 *   <li>{@code hf_model} — Hugging Face {@code owner/model}</li>
 *   <li>{@code hn_item} — Hacker News story id</li>
 *   <li>{@code tweet} — Twitter/X status id</li>
 * </ul>
 *
 * <p>The extractor is regex-based and intentionally avoids reaching into the
 * raw payload: V2 must stay deterministic without LLM parsing, and the URL
 * already carries the canonical identifier in every source Phase 16 targets.
 */
@Component
public class ExternalIdExtractor {

    private static final Pattern ARXIV_ABS = Pattern.compile("^https?://arxiv\\.org/(?:abs|pdf)/([^/?#\\s]+?)(?:\\.pdf)?(?:[/?#].*)?$");
    private static final Pattern GITHUB_REPO = Pattern.compile("^https?://github\\.com/([^/\\s]+)/([^/?#\\s]+)(?:[/?#].*)?$");
    private static final Pattern HF_MODEL = Pattern.compile("^https?://huggingface\\.co/([^/\\s]+)/([^/?#\\s]+?)(?:[/?#].*)?$");
    private static final Pattern HN_ITEM = Pattern.compile("^https?://news\\.ycombinator\\.com/item\\?id=(\\d+)$");
    private static final Pattern TWEET = Pattern.compile("^https?://(?:twitter|x)\\.com/([^/\\s]+)/status/(\\d+)$");
    private static final Pattern ARXIV_ID_IN_TEXT = Pattern.compile("\\b(?:arxiv[:\\s]+)?(\\d{4}\\.\\d{4,5})\\b", Pattern.CASE_INSENSITIVE);

    private final UrlCanonicalizer urlCanonicalizer;

    public ExternalIdExtractor(UrlCanonicalizer urlCanonicalizer) {
        this.urlCanonicalizer = urlCanonicalizer;
    }

    /**
     * Extracts external ids from the given source URL, source-typed external
     * id, and free-text title/summary.
     *
     * <p>The free-text scan is currently limited to arXiv ids, which are
     * frequently referenced in discussion-page titles
     * (e.g. {@code "Discussion: Scaling Laws (arXiv 2401.00123)"}).
     * Without this fallback, V2 would never L1-merge a paper and its
     * community discussion thread.
     *
     * @param sourceUrl  the canonical source URL (raw is fine; will be
     *                   canonicalized before pattern matching)
     * @param externalId the {@code hot_item.external_id} value, used as a
     *                   fallback when the URL alone is ambiguous
     * @param title      the hot item title (used for arXiv id fallback)
     * @param summary    the hot item summary (used for arXiv id fallback)
     * @return an ordered map of id-type to id-value (never {@code null},
     *         possibly empty)
     */
    public Map<String, String> extract(String sourceUrl, String externalId, String title, String summary) {
        Map<String, String> ids = new LinkedHashMap<>();
        String canonical = urlCanonicalizer.canonicalize(sourceUrl);
        if (canonical != null && !canonical.isBlank()) {
            match(canonical, ids);
        }
        // Fallback: scan title and summary for an arXiv id when the URL did
        // not already provide one.
        if (!ids.containsKey("arxiv")) {
            String fromText = scanArxivId(title, summary);
            if (fromText != null) {
                ids.put("arxiv", fromText);
            }
        }
        if (externalId != null && !externalId.isBlank()) {
            ids.putIfAbsent("external", externalId);
        }
        return ids;
    }

    /**
     * Convenience overload that skips the free-text scan.
     */
    public Map<String, String> extract(String sourceUrl, String externalId) {
        return extract(sourceUrl, externalId, null, null);
    }

    private String scanArxivId(String title, String summary) {
        String text = (title == null ? "" : title) + " " + (summary == null ? "" : summary);
        if (text.isBlank()) {
            return null;
        }
        Matcher m = ARXIV_ID_IN_TEXT.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private void match(String url, Map<String, String> ids) {
        Matcher arxiv = ARXIV_ABS.matcher(url);
        if (arxiv.matches()) {
            ids.putIfAbsent("arxiv", arxiv.group(1));
            return;
        }
        Matcher github = GITHUB_REPO.matcher(url);
        if (github.matches()) {
            ids.putIfAbsent("github", github.group(1) + "/" + github.group(2));
            return;
        }
        Matcher hf = HF_MODEL.matcher(url);
        if (hf.matches()) {
            ids.putIfAbsent("hf_model", hf.group(1) + "/" + hf.group(2));
            return;
        }
        Matcher hn = HN_ITEM.matcher(url);
        if (hn.matches()) {
            ids.putIfAbsent("hn_item", hn.group(1));
            return;
        }
        Matcher tweet = TWEET.matcher(url);
        if (tweet.matches()) {
            ids.putIfAbsent("tweet", tweet.group(2));
        }
    }

    /**
     * Returns the registered host (without leading {@code www.}) for the
     * given URL, or {@code null} if the URL is not parseable.
     *
     * <p>This is exposed as a convenience helper used by
     * {@code PublisherResolver} so URL parsing lives in one place.
     */
    public String hostOf(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(sourceUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            String lowered = host.toLowerCase(java.util.Locale.ROOT);
            return lowered.startsWith("www.") ? lowered.substring(4) : lowered;
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}
