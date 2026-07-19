package com.airadar.cluster.feature.extractor;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Picks the best-known event time for a hot item.
 *
 * <p>Order of preference:
 * <ol>
 *   <li>{@code publishedAt} — when the source carried a real publish time</li>
 *   <li>{@code firstSeenAt} — crawl-side fallback when no publish time exists</li>
 *   <li>{@code lastSeenAt} — last-resort fallback</li>
 *   <li>{@link Instant#now()} — purely defensive; should never be reached if
 *       the row was inserted through the normal pipeline</li>
 * </ol>
 */
@Component
public class EventTimeResolver {

    public Instant resolve(Instant publishedAt, Instant firstSeenAt, Instant lastSeenAt) {
        if (publishedAt != null) {
            return publishedAt;
        }
        if (firstSeenAt != null) {
            return firstSeenAt;
        }
        if (lastSeenAt != null) {
            return lastSeenAt;
        }
        return Instant.now();
    }
}
