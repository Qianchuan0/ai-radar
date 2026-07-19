package com.airadar.cluster.feature.extractor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves {@link EntityRef}s from a hot item's title and summary using the
 * configured {@link EntityAliasDictionary}.
 *
 * <p>Matching is word-boundary aware and case-insensitive. When multiple
 * aliases of the same canonical id match, only the first (longest-alias)
 * match is kept so we do not double-count the same entity.
 *
 * <p>The extractor does not perform fuzzy matching or LLM-based named entity
 * recognition. Unknown entities fall through to keyword matching downstream.
 */
@Component
public class EntityExtractor {

    private final EntityAliasDictionary dictionary;

    public EntityExtractor(EntityAliasDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public List<EntityRef> extract(String title, String summary) {
        String haystack = buildHaystack(title, summary);
        if (haystack.isEmpty()) {
            return List.of();
        }

        List<EntityRef> matches = new ArrayList<>();
        Set<String> seenCanonical = new LinkedHashSet<>();
        for (EntityAliasDictionary.Entry entry : dictionary.entries()) {
            String needle = EntityAliasDictionary.normalizeAliasForMatching(entry.getAlias());
            if (needle.isEmpty()) {
                continue;
            }
            if (seenCanonical.contains(entry.getCanonical())) {
                continue;
            }
            if (containsWordBoundary(haystack, needle)) {
                matches.add(new EntityRef(entry.getType(), entry.getCanonical(), entry.getDisplay()));
                seenCanonical.add(entry.getCanonical());
            }
        }
        return matches;
    }

    private static String buildHaystack(String title, String summary) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }
        if (summary != null && !summary.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(summary.trim());
        }
        String raw = sb.toString();
        if (raw.isEmpty()) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Returns true if {@code needle} appears in {@code haystack} on word
     * boundaries. Both arguments are already lower-cased and
     * non-alphanumeric-folded, so a direct regex with {@code \b} boundaries
     * is sufficient.
     */
    private static boolean containsWordBoundary(String haystack, String needle) {
        if (needle.isEmpty()) {
            return false;
        }
        String escaped = java.util.regex.Pattern.quote(needle);
        String boundary = needle.matches("^[a-z0-9].*") ? "\\b" + escaped + "\\b" : escaped;
        return java.util.regex.Pattern.compile(boundary).matcher(haystack).find();
    }
}
