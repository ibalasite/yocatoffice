package com.sayyogames.yocat.wallet.application;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.domain.Currency;

/**
 * &lt;&lt;DTO&gt;&gt; input command for {@link SpendWalletUseCase} (EDD §4.5.2 class-application, §4.5.4.1).
 *
 * <p>Maps {@code POST /wallet/spend} body {currency, amount, reason} plus the authenticated accountId.</p>
 */
public record SpendCommand(AccountId accountId, Currency currency, long amount, String reason) {
}
