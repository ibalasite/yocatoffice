package com.sayyogames.yocat.member;

import com.sayyogames.yocat.member.application.RegisterMemberUseCase;
import com.sayyogames.yocat.member.domain.MemberRepository;
import com.sayyogames.yocat.member.domain.PasswordHasher;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * member BC composition — registers the framework-free {@link RegisterMemberUseCase} (Orchestrate phase kept it
 * plain so it stays unit-testable) as a Spring bean, wired to the member domain ports (DIP, EDD §2 / §4.1).
 *
 * <p>Intra-module on purpose: wiring a bounded context's own use case belongs inside that module, not in a
 * central cross-module composition root (which would make the root module depend on member internals). It wires
 * only member-owned ports + the shared-kernel {@link DomainEventPublisher}, keeping the Spring Modulith module
 * graph acyclic (EDD §3.4 HC-5).</p>
 */
@Configuration
public class MemberBeansConfig {

    @Bean
    public RegisterMemberUseCase registerMemberUseCase(MemberRepository memberRepo, PasswordHasher passwordHasher,
                                                       DomainEventPublisher publisher) {
        return new RegisterMemberUseCase(memberRepo, passwordHasher, publisher);
    }
}
