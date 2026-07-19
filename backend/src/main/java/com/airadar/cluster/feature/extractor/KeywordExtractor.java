package com.airadar.cluster.feature.extractor;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts a small, deterministic keyword set from a title and summary.
 *
 * <p>Keywords are used by Level 3 similarity ({@code keywordOverlap * 0.15})
 * and as a fallback when no entity match fires. The extractor is
 * intentionally simple: lower-case, split on non-alphanumeric, drop
 * stop-words and short tokens, de-duplicate preserving order.
 */
@Component
public class KeywordExtractor {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "for", "to", "of", "in", "on", "at",
            "by", "with", "from", "into", "is", "are", "be", "been", "was", "were",
            "this", "that", "these", "those", "it", "its", "as", "if", "then", "than",
            "we", "you", "they", "he", "she", "our", "their", "his", "her",
            "new", "more", "most", "very", "just", "also", "about", "after", "before",
            "announces", "announced", "says", "said", "reports", "reported",
            "via", "re", "fw", "sec", "etc"
    );

    public java.util.List<String> extract(String title, String summary) {
        String text = (title == null ? "" : title) + " " + (summary == null ? "" : summary);
        if (text.isBlank()) {
            return java.util.List.of();
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        String[] tokens = lowered.split("[^a-z0-9]+");
        Set<String> unique = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            if (STOP_WORDS.contains(token)) {
                continue;
            }
            unique.add(token);
        }
        return Arrays.asList(unique.toArray(new String[0]));
    }
}
