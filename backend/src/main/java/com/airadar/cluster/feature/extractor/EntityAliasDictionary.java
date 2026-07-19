package com.airadar.cluster.feature.extractor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Resolves surface forms of well-known entities (products, organizations) to
 * stable canonical identifiers.
 *
 * <p>Phase 16 V1 ships a small, hand-curated dictionary covering the most
 * common AI ecosystem entities. The dictionary is intentionally narrow:
 * adding entries is cheap, but every entry directly affects Level 2 matching,
 * so entries must be deliberate. Unknown entities fall through to keyword
 * matching.
 *
 * <p>Canonicalization is asymmetric: every alias maps to exactly one
 * canonical id, but a single canonical id may have many aliases.
 *
 * <p>Examples:
 * <pre>
 *   GPT-5, GPT5, gpt 5  ->  gpt-5
 *   Claude Code, ClaudeCode -> claude-code
 *   Llama 3, Llama3, llama-3 -> llama-3
 *   OpenAI, Open AI -> openai
 *   Hugging Face, HF, huggingface -> hugging-face
 * </pre>
 */
@Component
public class EntityAliasDictionary {

    private final List<Entry> entriesByLengthDesc;

    public EntityAliasDictionary() {
        this(defaultEntries());
    }

    public EntityAliasDictionary(List<Entry> entries) {
        List<Entry> copy = new ArrayList<>(entries);
        copy.sort((a, b) -> Integer.compare(b.alias.length(), a.alias.length()));
        this.entriesByLengthDesc = Collections.unmodifiableList(copy);
    }

    /**
     * Returns dictionary entries sorted longest-alias-first so callers can
     * greedily match the most specific alias before shorter prefixes.
     */
    public List<Entry> entries() {
        return entriesByLengthDesc;
    }

    /**
     * Default curated entries for the Phase 16 V1 dictionary.
     *
     * <p>Each canonical id is registered with one or more aliases. The first
     * alias is also used as the display string.
     */
    public static List<Entry> defaultEntries() {
        List<Entry> entries = new ArrayList<>();
        // Products
        entries.addAll(product("gpt-5", "GPT-5", "GPT5", "gpt 5", "GPT–5"));
        entries.addAll(product("gpt-4", "GPT-4", "GPT4", "gpt 4"));
        entries.addAll(product("claude-code", "Claude Code", "ClaudeCode", "claude code"));
        entries.addAll(product("claude", "Claude"));
        entries.addAll(product("llama-3", "Llama 3", "Llama3", "llama-3", "Llama 3.1", "Llama-3.1"));
        entries.addAll(product("llama", "Llama"));
        entries.addAll(product("gemini", "Gemini"));
        entries.addAll(product("midjourney", "Midjourney"));
        entries.addAll(product("stable-diffusion", "Stable Diffusion", "StableDiffusion"));
        // Organizations
        entries.addAll(org("openai", "OpenAI", "Open AI"));
        entries.addAll(org("anthropic", "Anthropic"));
        entries.addAll(org("meta", "Meta"));
        entries.addAll(org("google", "Google"));
        entries.addAll(org("deepmind", "DeepMind"));
        entries.addAll(org("mistral", "Mistral", "Mistral AI"));
        entries.addAll(org("deepseek", "DeepSeek"));
        entries.addAll(org("hugging-face", "Hugging Face", "HuggingFace", "HF"));
        entries.addAll(org("nvidia", "NVIDIA"));
        entries.addAll(org("microsoft", "Microsoft"));
        entries.addAll(org("amazon", "Amazon"));
        entries.addAll(org("apple", "Apple"));
        return entries;
    }

    private static List<Entry> product(String canonical, String display, String... aliases) {
        return expand(EntityRef.Type.PRODUCT, canonical, display, aliases);
    }

    private static List<Entry> org(String canonical, String display, String... aliases) {
        return expand(EntityRef.Type.ORG, canonical, display, aliases);
    }

    private static List<Entry> expand(EntityRef.Type type, String canonical, String display, String[] aliases) {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(type, canonical, display, display));
        for (String alias : aliases) {
            out.add(new Entry(type, canonical, display, alias));
        }
        return out;
    }

    /**
     * Lower-cased alias for matching, with non-alphanumeric runs collapsed
     * to a single space so {@code "GPT-5"} and {@code "GPT 5"} become the
     * same needle.
     */
    public static String normalizeAliasForMatching(String alias) {
        if (alias == null || alias.isBlank()) {
            return "";
        }
        return alias.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * One dictionary row: alias surface form plus the canonical id and
     * entity type it resolves to.
     */
    public static final class Entry {

        private final EntityRef.Type type;
        private final String canonical;
        private final String display;
        private final String alias;

        public Entry(EntityRef.Type type, String canonical, String display, String alias) {
            this.type = type;
            this.canonical = canonical;
            this.display = display;
            this.alias = alias;
        }

        public EntityRef.Type getType() {
            return type;
        }

        public String getCanonical() {
            return canonical;
        }

        public String getDisplay() {
            return display;
        }

        public String getAlias() {
            return alias;
        }
    }
}
