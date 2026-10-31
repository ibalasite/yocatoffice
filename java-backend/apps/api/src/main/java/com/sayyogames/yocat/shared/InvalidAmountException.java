package com.sayyogames.yocat.shared;

/**
 * Domain error — an amount falls outside the legal range [1, 10_000_000] (CONSTANTS §8,
 * EDD §4.7 Algorithm 1). Maps to HTTP 400 at the presentation layer.
 */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(long amount) {
        super("金額不正確: " + amount);
    }
}
