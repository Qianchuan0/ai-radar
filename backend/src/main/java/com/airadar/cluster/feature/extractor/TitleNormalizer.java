package com.airadar.cluster.feature.extractor;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes a hot item title for deterministic matching.
 *
 * <p>Phase 16 V2 only uses the normalized title for Level 3 similarity
 * scoring. The transformation is intentionally lossy:
 * <ul>
 *   <li>lower-case</li>
 *   <li>strip editorial prefixes like {@code "breaking:"},
 *       {@code "[release]"}, {@code "(update)"} that add noise without
 *       helping event identity</li>
 *   <li>replace non-alphanumeric runs with single spaces</li>
 *   <li>collapse repeated whitespace</li>
 * </ul>
 *
 * <p>URLs, punctuation, and casing differences should not survive this step.
 */
@Component
public class TitleNormalizer {

    private static final Pattern EDITORIAL_PREFIX = Pattern.compile(
            "^(breaking|announcement|news|update|update\\s*\\d*|hot|exclusive|"
                    + "\\[\\s*[a-z\\s_-]+\\s*\\]|\\(\\s*[a-z\\s_-]+\\s*\\))\\s*[:\\-]\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NON_ALNUM_RUN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String trimmed = input.trim();
        String withoutPrefix = EDITORIAL_PREFIX.matcher(trimmed).replaceFirst("");
        String lowered = withoutPrefix.toLowerCase(Locale.ROOT);
        String replaced = NON_ALNUM_RUN.matcher(lowered).replaceAll(" ");
        return WHITESPACE_RUN.matcher(replaced).replaceAll(" ").trim();
    }
}
