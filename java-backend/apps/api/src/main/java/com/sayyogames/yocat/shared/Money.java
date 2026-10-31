package com.sayyogames.yocat.shared;

import java.util.Objects;

/**
 * Value Object — a monetary quantity in the smallest currency unit (EDD §4.5.2 class-domain).
 *
 * <p>Immutable. The public factory {@link #of(long)} enforces the non-negativity invariant
 * (CONSTANTS §8 TOPUP_AMOUNT_MIN=1 ⇒ a stored amount is never below 0). Arithmetic
 * ({@link #plus}, {@link #minus}) returns new instances and may transiently represent a negative
 * result so that callers can detect underflow via {@link #isNegative()} before committing it.
 */
public final class Money {

    private final long amount;

    private Money(long amount) {
        this.amount = amount;
    }

    /**
     * Constructs a non-negative Money. A raw value below 0 violates the Money invariant
     * (EDD §4.5.2 Invariant Table) and is rejected.
     */
    public static Money of(long raw) {
        if (raw < 0) {
            throw new IllegalArgumentException("金額不可為負: " + raw);
        }
        return new Money(raw);
    }

    public long amount() {
        return amount;
    }

    /** Returns {@code this + other} as a new Money (no mutation). */
    public Money plus(Money other) {
        return new Money(this.amount + other.amount);
    }

    /**
     * Returns {@code this - other} as a new Money. The result may be negative (underflow); the caller
     * detects it through {@link #isNegative()} and must not persist a negative balance.
     */
    public Money minus(Money other) {
        return new Money(this.amount - other.amount);
    }

    /** True when this amount is below zero (underflow flag for balance guards). */
    public boolean isNegative() {
        return amount < 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return this.amount == other.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(amount);
    }

    @Override
    public String toString() {
        return "Money(" + amount + ")";
    }
}
