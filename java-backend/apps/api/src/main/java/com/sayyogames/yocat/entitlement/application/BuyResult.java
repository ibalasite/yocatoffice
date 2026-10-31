package com.sayyogames.yocat.entitlement.application;

import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.wallet.application.WalletView;
import java.util.List;

/**
 * &lt;&lt;DTO&gt;&gt; output of {@link BuyEntitlementUseCase} (EDD §4.5.2 class-application, §4.5.4.3).
 *
 * <p>Maps the {@code POST /entitlements/buy} 200 body {wallet, entitlements}: post-debit wallet view
 * plus the account's owned entitlements after the grant.</p>
 */
public record BuyResult(WalletView wallet, List<Entitlement> entitlements) {
}
