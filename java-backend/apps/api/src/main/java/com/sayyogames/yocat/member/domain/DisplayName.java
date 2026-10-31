package com.sayyogames.yocat.member.domain;

import java.util.Objects;

/**
 * Value Object — the company / boss display name (EDD §4.5.2 class-domain, Account Invariant Table:
 * 長度 ∈ [1,24]). Pure domain — no Spring, no JDBC.
 *
 * <p>Immutable. {@link #of(String)} enforces the length invariant counted in code points
 * (CONSTANTS §8 DISPLAY_NAME_MIN=1, DISPLAY_NAME_MAX=24; PRD §5.1 AC-3), so full-width characters
 * such as CJK count as one "字". Out-of-range or null values are rejected.
 */
public final class DisplayName {

    public static final int MIN = 1;
    public static final int MAX = 24;

    private final String value;

    private DisplayName(String value) {
        this.value = value;
    }

    /**
     * Constructs a DisplayName of 1–24 code points. A null or out-of-range value violates the
     * Account display-name invariant and is rejected.
     */
    public static DisplayName of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("公司/老闆名不可為 null");
        }
        int length = raw.codePointCount(0, raw.length());
        if (length < MIN || length > MAX) {
            throw new IllegalArgumentException("公司/老闆名需 " + MIN + "–" + MAX + " 字: 實得 " + length);
        }
        return new DisplayName(raw);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisplayName other)) {
            return false;
        }
        return this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
