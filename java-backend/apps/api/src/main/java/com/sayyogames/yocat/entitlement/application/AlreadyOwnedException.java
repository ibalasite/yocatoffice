package com.sayyogames.yocat.entitlement.application;

/**
 * Raised when an account already owns the requested (kind, itemId) entitlement (EDD §4.5.4.4 → HTTP 409).
 *
 * <p>Enforces the {@code entitlement.entitlements UNIQUE(account_id, kind, item_id)} idempotency rule at the
 * application layer so no wallet debit or grant occurs for a duplicate purchase.</p>
 */
public class AlreadyOwnedException extends RuntimeException {

    public AlreadyOwnedException() {
        super("已經擁有這個項目");
    }
}
