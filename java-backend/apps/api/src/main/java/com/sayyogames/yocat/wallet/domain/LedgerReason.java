package com.sayyogames.yocat.wallet.domain;

/**
 * Ledger reason enumeration owned by the wallet BC
 * (EDD §4.5.2 class-domain, CONSTANTS §9 reason set).
 */
public enum LedgerReason {
    INIT,
    TOPUP,
    BUY_COSMETIC,
    BUY_DREAM,
    BUY_SCENARIO,
    RESCUE
}
