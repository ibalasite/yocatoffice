package com.sayyogames.yocat.deposit.domain;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — deposit BC order (SCHEMA §3.3.2 {@code deposit.deposit_order}, PK {@code order_id},
 * UNIQUE {@code idempotency_key}). Phase A fake-pay settles immediately, so a created order is always
 * {@code confirmed} (API §3 deposit) and triggers {@code DepositConfirmed} to credit the wallet.
 *
 * @param orderId        the order business key (text)
 * @param accountId      the paying account id
 * @param packId         the purchased pack id
 * @param currency       credited currency (gems/cash)
 * @param amount         credited amount (&gt; 0)
 * @param status         order status (Phase A: {@code confirmed})
 * @param idempotencyKey the client Idempotency-Key that created this order
 */
public record DepositOrder(String orderId, long accountId, String packId, String currency, long amount,
                           String status, String idempotencyKey) {
}
