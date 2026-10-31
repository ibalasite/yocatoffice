package com.sayyogames.yocat.member.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object — a member's email address (EDD §4.5.2 class-domain, Account Invariant Table:
 * 格式合法、全域唯一). Pure domain — no Spring, no JDBC.
 *
 * <p>Immutable. {@link #of(String)} trims the raw input and normalises it to lower case so that
 * global-uniqueness comparison (and the SCHEMA {@code member.accounts.email} UNIQUE constraint) is
 * case-insensitive, then validates the structural format. Invalid formats are rejected.
 */
public final class Email {

    private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    /**
     * Constructs a normalised Email. The raw value must be non-null and match the
     * {@code local@domain.tld} shape; otherwise it violates the Account email invariant and is
     * rejected.
     */
    public static Email of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("email 不可為 null");
        }
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(normalised).matches()) {
            throw new IllegalArgumentException("email 格式不正確: " + raw);
        }
        return new Email(normalised);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Email other)) {
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
