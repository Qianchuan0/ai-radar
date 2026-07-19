/**
 * Phase 16 item feature extraction pipeline.
 *
 * <p>The extractors here are intentionally deterministic and side-effect-free
 * (except for {@link com.airadar.cluster.feature.extractor.ItemFeatureExtractor#extractAndPersist},
 * which also writes the feature row). No LLM, no embedding, no network.
 *
 * <p>Vocabulary:
 * <ul>
 *   <li>{@link com.airadar.cluster.feature.extractor.TitleNormalizer} — lower-cases
 *       and folds punctuation for similarity scoring</li>
 *   <li>{@link com.airadar.cluster.feature.extractor.ExternalIdExtractor} —
 *       typed ids from URLs (arXiv id, GitHub repo, Hugging Face model,
 *       HN story, tweet)</li>
 *   <li>{@link com.airadar.cluster.feature.extractor.EntityAliasDictionary} +
 *       {@link com.airadar.cluster.feature.extractor.EntityExtractor} —
 *       canonicalize known products and organizations via a curated alias
 *       table</li>
 *   <li>{@link com.airadar.cluster.feature.extractor.KeywordExtractor} —
 *       simple keyword set for Level 3 overlap scoring</li>
 *   <li>{@link com.airadar.cluster.feature.extractor.EventTimeResolver} —
 *       picks publishedAt &gt; firstSeenAt &gt; lastSeenAt</li>
 *   <li>{@link com.airadar.cluster.feature.extractor.PublisherResolver} —
 *       host-derived publisher domain</li>
 *   <li>{@link com.airadar.cluster.feature.extractor.EventTypeResolver} —
 *       coarse event classification (RELEASE, UPDATE, FUNDING, etc.)</li>
 * </ul>
 */
package com.airadar.cluster.feature.extractor;
