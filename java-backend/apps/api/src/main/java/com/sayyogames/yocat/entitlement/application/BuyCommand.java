package com.sayyogames.yocat.entitlement.application;

import com.sayyogames.yocat.entitlement.domain.EntitlementKind;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.domain.Currency;

/**
 * &lt;&lt;DTO&gt;&gt; input command for {@link BuyEntitlementUseCase} (EDD §4.5.2 class-application, §4.5.4.3).
 *
 * <p>Maps {@code POST /entitlements/buy} body {kind, itemId, currency, amount} plus authenticated accountId.
 * {@code kind} is already parsed to the {@link EntitlementKind} enum at the presentation boundary.</p>
 */
public record BuyCommand(AccountId accountId, EntitlementKind kind, String itemId, Currency currency, long amount) {
}
