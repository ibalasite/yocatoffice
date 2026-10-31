package com.sayyogames.yocat.wallet.domain;

/**
 * Domain error — a debit was rejected because the current balance is below the requested amount
 * (EDD §4.5.6 Wallet Invalid-Transition Table, §4.7 Algorithm 1). Maps to HTTP 402 and rolls back
 * the transaction without writing a ledger line. Carries the unchanged balance for the response.
 */
public class InsufficientBalanceException extends RuntimeException {

    private final long balance;

    public InsufficientBalanceException(long balance) {
        super("餘額不足");
        this.balance = balance;
    }

    public long balance() {
        return balance;
    }
}
