package com.sayyogames.yocat.entitlement;

import com.sayyogames.yocat.entitlement.application.BuyEntitlementUseCase;
import com.sayyogames.yocat.entitlement.domain.EntitlementRepository;
import com.sayyogames.yocat.wallet.api.WalletService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * entitlement BC composition — registers the framework-free {@link BuyEntitlementUseCase} (Orchestrate phase
 * kept it plain) as a Spring bean, wired to the entitlement domain port and the wallet BC's Public API
 * ({@link WalletService}) for the atomic buy-debit (EDD §4.1 / §4.3; the entitlement→wallet dependency is a
 * forward edge in the module DAG, HC-5).
 *
 * <p>Intra-module on purpose: wiring a bounded context's own use case belongs inside that module, not in a
 * central cross-module composition root.</p>
 */
@Configuration
public class EntitlementBeansConfig {

    @Bean
    public BuyEntitlementUseCase buyEntitlementUseCase(EntitlementRepository entRepo, WalletService walletService) {
        return new BuyEntitlementUseCase(entRepo, walletService);
    }
}
