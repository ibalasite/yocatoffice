package com.sayyogames.yocat;

import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.wallet.api.WalletService;
import com.sayyogames.yocat.wallet.application.SpendWalletUseCase;
import com.sayyogames.yocat.wallet.application.TopUpWalletUseCase;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-root composition for the wallet BC's framework-free Public API / use cases (the Domain/Orchestrate
 * phases produced them with no Spring annotations so they stay unit-testable). It wires only the wallet BC's
 * exposed Public NamedInterface types ({@code wallet.api} / {@code wallet.application} / {@code wallet.domain})
 * plus the shared-kernel {@link DomainEventPublisher}, all of which are exposed modules — so this root
 * configuration depends on no non-exposed BC internal and the Spring Modulith module graph stays acyclic
 * (EDD §3.4 HC-5).
 *
 * <p>The member and entitlement use cases are wired intra-module ({@code MemberBeansConfig} /
 * {@code EntitlementBeansConfig}) because those references would otherwise reach into member/entitlement
 * internals from the application root.</p>
 */
@Configuration
public class DomainBeansConfig {

    @Bean
    public WalletService walletService(WalletRepository walletRepo, DomainEventPublisher publisher) {
        return new WalletService(walletRepo, publisher);
    }

    @Bean
    public SpendWalletUseCase spendWalletUseCase(WalletRepository walletRepo, DomainEventPublisher publisher) {
        return new SpendWalletUseCase(walletRepo, publisher);
    }

    @Bean
    public TopUpWalletUseCase topUpWalletUseCase(WalletRepository walletRepo, DomainEventPublisher publisher) {
        return new TopUpWalletUseCase(walletRepo, publisher);
    }
}
