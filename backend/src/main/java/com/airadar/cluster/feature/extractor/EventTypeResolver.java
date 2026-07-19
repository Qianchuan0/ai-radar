package com.airadar.cluster.feature.extractor;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies a hot item into a coarse {@link EventType} used by the layered
 * match rules.
 *
 * <p>The classifier is keyword-driven and deterministic. When multiple
 * categories match, the first one in priority order wins. Priority is set so
 * the most disambiguating categories (security, pricing, acquisition) win
 * over generic release/update language: a "vulnerability disclosed in
 * release X" must classify as {@link EventType#SECURITY_INCIDENT}, not
 * {@link EventType#RELEASE}.
 *
 * <p>{@link EventType#UNKNOWN} is the fallback when no signal fires.
 */
@Component
public class EventTypeResolver {

    private static final Rule[] RULES_IN_PRIORITY_ORDER = {
            new Rule(EventType.SECURITY_INCIDENT, "vulnerab", "security flaw", " cve", "exploit", "0day", "zero-day", "leak"),
            new Rule(EventType.ACQUISITION, "acquir", "acquisition", "buys ", "buyout", "merger", "merges with"),
            new Rule(EventType.FUNDING, "raises ", "raising ", "funding round", "series a", "series b", "series c", "investment", "valuation"),
            new Rule(EventType.PRICING, "pricing", "price cut", "price increase", "fee", "subscription cost", "api cost"),
            new Rule(EventType.POLICY, "policy", "regulation", "regulated", "banned", "ban ", "compliance"),
            new Rule(EventType.BENCHMARK, "benchmark", "leaderboard", "evaluates", "evaluation results", "comparison"),
            new Rule(EventType.RESEARCH, " arxiv", "paper:", "research paper", "we propose", "we present", "in this paper"),
            new Rule(EventType.OPEN_SOURCE, "open source", "open-source", "open weights", "open-weights", "released under mit", "apache 2.0"),
            new Rule(EventType.UPDATE, "update", "patch", "upgrade", "hotfix", "version bump", "rolls out", "ships patch"),
            new Rule(EventType.RELEASE, "launches", "launch", "announces", "announce", "unveils", "unveil", "ships", "released", "releases", "introduces", "debuts", "publishes", "publish", "drops", "general availability", "ga "),
    };

    public EventType resolve(String normalizedTitle, String summary) {
        String haystack = buildHaystack(normalizedTitle, summary);
        if (haystack.isEmpty()) {
            return EventType.UNKNOWN;
        }
        for (Rule rule : RULES_IN_PRIORITY_ORDER) {
            if (rule.matches(haystack)) {
                return rule.eventType;
            }
        }
        return EventType.UNKNOWN;
    }

    private static String buildHaystack(String title, String summary) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(' ').append(title.trim());
        }
        if (summary != null && !summary.isBlank()) {
            sb.append(' ').append(summary.trim());
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static final class Rule {

        final EventType eventType;
        final Pattern[] patterns;

        Rule(EventType eventType, String... needles) {
            this.eventType = eventType;
            this.patterns = new Pattern[needles.length];
            for (int i = 0; i < needles.length; i++) {
                this.patterns[i] = Pattern.compile(Pattern.quote(needles[i]), Pattern.LITERAL);
            }
        }

        boolean matches(String haystack) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(haystack).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
