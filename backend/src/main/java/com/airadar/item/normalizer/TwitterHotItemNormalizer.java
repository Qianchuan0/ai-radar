package com.airadar.item.normalizer;

import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.model.NormalizedHotItem;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TwitterHotItemNormalizer implements HotItemNormalizer {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#\\w+");
    private static final int TITLE_MAX_LENGTH = 100;

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public TwitterHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.TWITTER;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String tweetId = cleanText(payload.path("tweetId").asText(""));
        String text = cleanText(payload.path("text").asText(""));
        String authorName = cleanText(payload.path("authorName").asText(""));
        String authorUsername = cleanText(payload.path("authorUsername").asText(""));
        long authorFollowers = payload.path("authorFollowers").asLong(0L);
        boolean authorVerified = payload.path("authorVerified").asBoolean(false);
        int likeCount = payload.path("likeCount").asInt(0);
        int retweetCount = payload.path("retweetCount").asInt(0);
        int replyCount = payload.path("replyCount").asInt(0);
        int quoteCount = payload.path("quoteCount").asInt(0);
        long viewCount = payload.path("viewCount").asLong(0L);
        String query = cleanText(payload.path("query").asText(""));

        if (tweetId.isBlank() || text.isBlank()) {
            return Optional.empty();
        }

        // Build title (first 100 chars of tweet text)
        String title = text.length() > TITLE_MAX_LENGTH
                ? text.substring(0, TITLE_MAX_LENGTH)
                : text;

        // Build source URL
        String sourceUrl = "https://twitter.com/i/web/status/" + tweetId;
        sourceUrl = urlCanonicalizer.canonicalize(sourceUrl);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        // Build author (prefer name, fallback to username)
        String author = !authorName.isBlank() ? authorName : authorUsername;

        // Build tags (twitter + query + hashtags)
        ArrayNode tags = objectMapper.createArrayNode();
        Set<String> seenTags = new HashSet<>();
        addTag(tags, seenTags, "twitter");
        if (!query.isBlank()) {
            addTag(tags, seenTags, query);
        }
        // Extract hashtags from tweet text
        Matcher matcher = HASHTAG_PATTERN.matcher(text);
        while (matcher.find()) {
            String hashtag = matcher.group();
            addTag(tags, seenTags, hashtag);
        }

        // Build metrics
        long points = (long) likeCount + (long) retweetCount * 3 + viewCount / 100;
        int commentsCount = replyCount + quoteCount;

        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", points);
        metrics.put("commentsCount", commentsCount);
        metrics.put("viewCount", viewCount);
        metrics.put("likeCount", likeCount);
        metrics.put("retweetCount", retweetCount);
        metrics.put("replyCount", replyCount);
        metrics.put("quoteCount", quoteCount);
        metrics.put("authorFollowers", authorFollowers);
        metrics.put("authorVerified", authorVerified);

        return Optional.of(new NormalizedHotItem(
                "POST",
                title,
                text,
                sourceUrl,
                author,
                tags,
                metrics,
                sha256Hex(tweetId.toLowerCase()),
                rawItem.getPublishedAt()
        ));
    }

    private void addTag(ArrayNode tags, Set<String> seenTags, String value) {
        String normalized = cleanText(value);
        if (!normalized.isBlank() && seenTags.add(normalized.toLowerCase())) {
            tags.add(normalized);
        }
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
