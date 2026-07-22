package com.airadar.scoring.calculator;

import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the evidence-diversity dimension from the variety of independent
 * source roles backing the cluster.
 *
 * <p>The core rule is deduplication: when multiple search engines (Bing,
 * DuckDuckGo, Sogou) point at the same canonical URL, they count as a single
 * DISCOVERY evidence rather than three independent sources. Non-search roles
 * contribute one evidence entry each.
 *
 * <p><b>Phase 18B refactor:</b> uses the pre-computed
 * {@link ScoringContext#dedupedDiscoveryUrls()} set built by
 * {@code CrossSourceScoreV2Strategy.buildContext}, so evidence-diversity and
 * trend deduplication share the same canonicalization source. The
 * {@link UrlCanonicalizer} dependency is kept only so the calculator can be
 * constructed in isolation by existing unit tests.
 */
@Component
public class EvidenceDiversityCalculator implements ScoreCalculator {

    public static final String NAME = "evidenceDiversity";
    public static final double WEIGHT = 0.10;
    private static final double SCORE_PER_ROLE = 25.0;

    private final UrlCanonicalizer urlCanonicalizer;

    public EvidenceDiversityCalculator(UrlCanonicalizer urlCanonicalizer) {
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ScoreComponent compute(ScoringContext context) {
        List<String> reasons = new ArrayList<>();
        Set<SourceRole> distinctRoles = new HashSet<>();

        for (NormalizedSignal signal : context.signals().values()) {
            SourceRole role = signal.sourceRole();
            if (role != null && role != SourceRole.DISCOVERY) {
                distinctRoles.add(role);
            }
        }

        Set<String> discoveryUrls = context.dedupedDiscoveryUrls();
        if (discoveryUrls != null && !discoveryUrls.isEmpty()) {
            distinctRoles.add(SourceRole.DISCOVERY);
        }

        int dedupedSearchSources = discoveryUrls == null ? 0 : discoveryUrls.size();
        double score = Math.min(100.0, distinctRoles.size() * SCORE_PER_ROLE);

        reasons.add("distinct_roles=" + distinctRoles);
        reasons.add("deduped_search_urls=" + dedupedSearchSources);
        return new ScoreComponent(NAME, score, WEIGHT, reasons);
    }
}
