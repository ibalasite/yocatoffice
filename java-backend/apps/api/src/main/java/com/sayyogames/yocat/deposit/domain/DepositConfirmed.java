package com.sayyogames.yocat.deposit.domain;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * &lt;&lt;DomainEvent&gt;&gt; — owned by the deposit BC (EDD §4.6.1 {@code deposit.order.confirmed} v1).
 * Published when Phase A fake-pay (or Phase B payment confirmation) settles an order; consumed by the wallet
 * BC to credit the paid currency (EDD §3.4, §4.6.1). Pure domain — no Spring, no JDBC.
 *
 * <p>The {@code currency} is carried as its lower-case wire token ({@code gems}/{@code cash}) rather than the
 * wallet BC's {@code Currency} enum, so the deposit module does not depend on the wallet module: the only
 * cross-module edge is wallet→deposit (the consumer references this published event), keeping the module graph
 * an acyclic DAG (HC-5; Spring Modulith {@code verify()}). The consuming wallet listener maps the token to its
 * own {@code Currency}.</p>
 *
 * @param accountId  the paying account's identity
 * @param orderId    the confirmed deposit order id
 * @param currency   the credited currency wire token ({@code gems}/{@code cash})
 * @param amount     the credited amount (&gt; 0)
 * @param occurredAt the instant the payment was confirmed
 */
public record DepositConfirmed(
        AccountId accountId,
        long orderId,
        String currency,
        long amount,
        Instant occurredAt) implements DomainEvent {

    public DepositConfirmed {
        Objects.requireNonNull(accountId, "accountId 不可為 null");
        Objects.requireNonNull(currency, "currency 不可為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不可為 null");
    }

    /** Raises the event stamped at the current instant. */
    public DepositConfirmed(AccountId accountId, long orderId, String currency, long amount) {
        this(accountId, orderId, currency, amount, Instant.now());
    }
}
