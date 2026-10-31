package com.sayyogames.yocat.shared;

/**
 * Value Object — stable account identity shared across every Bounded Context
 * (SharedKernel, EDD §4.5.2 class-domain).
 *
 * <p>Immutable with value-equality semantics. SCHEMA {@code member.accounts.id} is a
 * {@code BIGSERIAL} (strictly positive), so {@link #of(long)} enforces the positivity invariant.
 */
public final class AccountId {

    private final long value;

    private AccountId(long value) {
        this.value = value;
    }

    /**
     * Constructs an AccountId. A non-positive raw value cannot correspond to a persisted
     * {@code BIGSERIAL} identity and is rejected (EDD §4.5.2 Invariant Table).
     */
    public static AccountId of(long raw) {
        if (raw <= 0) {
            throw new IllegalArgumentException("帳號 ID 必須為正 (BIGSERIAL > 0): " + raw);
        }
        return new AccountId(raw);
    }

    public long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountId other)) {
            return false;
        }
        return this.value == other.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "AccountId(" + value + ")";
    }
}
