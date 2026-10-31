package com.sayyogames.yocat.deposit.domain;

import java.util.Optional;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link DepositOrder} aggregate (deposit BC, SCHEMA §3.3.2).
 *
 * <p>Domain-owned interface; the JDBC adapter is injected via DI (DIP). The {@code idempotency_key} UNIQUE
 * index backs replay/conflict detection for {@code POST /deposit/checkout} (API §7).</p>
 */
public interface DepositOrderRepository {

    /** Resolve the order a given Idempotency-Key already created; empty on the first use. Caller: CheckoutService. */
    Optional<DepositOrder> findByIdempotencyKey(String idempotencyKey);

    /** Persist a new confirmed order (INSERT). Caller: CheckoutService (Phase A). */
    DepositOrder save(DepositOrder order);
}
