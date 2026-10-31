package com.sayyogames.yocat.wallet.application;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.domain.Currency;

/**
 * &lt;&lt;DTO&gt;&gt; input command for {@link TopUpWalletUseCase} (EDD §4.5.2 class-application).
 *
 * <p>Maps {@code POST /wallet/topup} body {currency, amount, reason}; credit path, no balance check.</p>
 */
public record TopUpCommand(AccountId accountId, Currency currency, long amount, String reason) {
}
