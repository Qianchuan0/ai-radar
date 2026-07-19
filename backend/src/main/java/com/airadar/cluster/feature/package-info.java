/**
 * Phase 16 item feature layer.
 *
 * <p>Stores the deterministic feature vector extracted from each
 * {@code hot_item} so the V2 clustering pipeline (candidate retrieval +
 * layered match rules) can reuse it across match attempts without
 * re-deriving from source text.
 *
 * <p>The feature schema is intentionally narrow in V1 of Phase 16: title
 * normalization, canonical URL, publisher domain, event time, external ids
 * (arXiv id, GitHub repo, Hugging Face model id, etc.), entities (resolved
 * product / org / concept identifiers), keywords, and event type. No
 * embedding or LLM-derived fields belong here.
 */
package com.airadar.cluster.feature;
