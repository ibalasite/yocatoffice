package com.sayyogames.yocat.platform.web;

/**
 * Presentation error — an {@code Idempotency-Key} was reused with a different request payload
 * (deposit checkout, API §7). Maps to HTTP 409; no new order is created.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
