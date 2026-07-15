package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

/**
 * Signal adapter for GitHub repositories.
 *
 * <p>Maps GitHub metrics to signal components:
 * <ul>
 *   <li>stargazersCount, watchersCount → attention (weak signal)</li>
 *   <li>openIssuesCount → discussion (issues indicate engagement)</li>
 *   <li>stargazersCount, forksCount, watchersCount → adoption (primary adoption signal)</li>
 * </ul>
 *
 * <p>GitHub represents developer adoption through stars, forks, and watchers.
 * The adoption signal is the strongest component, reflecting actual usage.
 */
@Component
public class GitHubSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_STARS = 5000;
    private static final int MAX_ISSUES = 500;

    @Override
    public SourceType supportedType() {
        return SourceType.GITHUB;
    }

    @Override
    public NormalizedSignal adapt(HotItemEntity hotItem) {
        if (hotItem.getMetrics() == null) {
            return zeroSignal();
        }

        int stars = hotItem.getMetrics().path("stargazersCount").asInt(0);
        int forks = hotItem.getMetrics().path("forksCount").asInt(0);
        int watchers = hotItem.getMetrics().path("watchersCount").asInt(0);
        int openIssues = hotItem.getMetrics().path("openIssuesCount").asInt(0);

        // Attention: weak signal from stars and watchers
        double attention = normalizeLog(stars + watchers, MAX_STARS);

        // Discussion: issues indicate community engagement
        double discussion = normalizeLog(openIssues, MAX_ISSUES);

        // Adoption: primary signal from stars, forks, and watchers
        // Combine all three for a comprehensive adoption score
        int adoptionRaw = stars + (forks * 2) + watchers;  // forks weighted higher
        double adoption = normalizeLog(adoptionRaw, MAX_STARS * 2);

        return NormalizedSignal.of(
            SourceType.GITHUB,
            SourceRole.ADOPTION,
            attention,
            discussion,
            adoption,
            hotItem.getMetrics()
        );
    }

    private double normalizeLog(int value, int max) {
        int capped = Math.max(0, Math.min(value, max));
        return Math.log1p(capped) / Math.log1p(max) * 100.0;
    }

    private NormalizedSignal zeroSignal() {
        return NormalizedSignal.of(
            SourceType.GITHUB,
            SourceRole.ADOPTION,
            0.0,
            0.0,
            0.0,
            null
        );
    }
}
