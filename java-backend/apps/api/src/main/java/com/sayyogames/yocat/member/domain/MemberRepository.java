package com.sayyogames.yocat.member.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link Account} aggregate (member BC).
 *
 * <p>Domain-owned interface; the JDBC adapter lives in the infrastructure layer and is injected by
 * Spring DI (Dependency Inversion, EDD §2 / HC-5). The domain layer imports no framework/JDBC type.
 * Authoritative signatures: EDD §4.5.2 Class Diagram (class-domain).</p>
 */
public interface MemberRepository {

    /** Load an account by its globally unique email; empty when none exists. Caller: LoginUseCase (EDD §4.5.4.5 pattern). */
    Optional<Account> findByEmail(Email email);

    /** Load an account by identity; empty when absent. Caller: GetMeUseCase / MemberService.verifySession (EDD §4.2). */
    Optional<Account> findById(AccountId id);

    /**
     * Persist a new or changed account and return its identity (INSERT ... RETURNING id).
     * Caller: RegisterMemberUseCase (EDD §4.5.4.5). May propagate the email-uniqueness violation that maps to 409.
     */
    AccountId save(Account account);

    /** True when an account already owns this email (register pre-check → 409). Caller: RegisterMemberUseCase (EDD §4.5.4.5). */
    boolean existsByEmail(Email email);
}
