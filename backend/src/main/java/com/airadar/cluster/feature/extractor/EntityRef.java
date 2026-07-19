package com.airadar.cluster.feature.extractor;

import java.util.Objects;

/**
 * One resolved entity mention extracted from a hot item.
 *
 * <p>{@link #type} is coarse ({@link Type#PRODUCT}, {@link Type#ORG}, etc.).
 * {@link #value} is the canonical identifier produced by the
 * {@code EntityAliasDictionary} (e.g. {@code "gpt-5"} for all of GPT-5 /
 * GPT5 / gpt 5). {@link #display} preserves the original surface form for
 * debugging and reports.
 */
public final class EntityRef {

    public enum Type {
        PRODUCT,
        ORG,
        CONCEPT,
        PERSON
    }

    private final Type type;
    private final String value;
    private final String display;

    public EntityRef(Type type, String value, String display) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = Objects.requireNonNull(value, "value");
        this.display = Objects.requireNonNull(display, "display");
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getDisplay() {
        return display;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityRef other)) {
            return false;
        }
        return type == other.type && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return type + ":" + value;
    }
}
