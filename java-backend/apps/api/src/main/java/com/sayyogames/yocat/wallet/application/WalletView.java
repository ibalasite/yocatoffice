package com.sayyogames.yocat.wallet.application;

/**
 * &lt;&lt;DTO&gt;&gt; read-model view of a wallet returned by use cases / WalletService (EDD §4.5.2 class-application).
 *
 * <p>Does not leak the {@code Wallet} aggregate; carries the post-operation balances per currency.</p>
 */
public record WalletView(long cash, long gems) {
}
