package com.sayyogames.yocat.member.application;

import com.sayyogames.yocat.member.domain.Account;
import com.sayyogames.yocat.member.domain.DisplayName;
import com.sayyogames.yocat.member.domain.Email;
import com.sayyogames.yocat.member.domain.MemberRepository;
import com.sayyogames.yocat.member.domain.PasswordHasher;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.shared.MemberRegistered;

/**
 * &lt;&lt;UseCase&gt;&gt; member registration with cross-BC event (EDD §4.5.4.5; {@code POST /auth/register}).
 *
 * <p>Orchestration (EDD §4.5.4.5 / §5.4): the domain value objects validate email format and display-name
 * length (→ 400); the raw password length is checked against CONSTANTS §8 PASSWORD_MIN (PRD §5.1 AC-5);
 * email global-uniqueness is pre-checked (→ 409); the password is hashed through the {@link PasswordHasher}
 * port; the account is persisted; and {@code MemberRegistered} is published <em>after</em> persistence so the
 * wallet BC's listener can provision the initial wallet + INIT ledger (EDD §3.4 / §7.1). Depends on the
 * domain ports only (DIP).</p>
 *
 * <p>Note: the password port is the domain {@code PasswordHasher} (encode/matches) shipped by
 * domain-entities; EDD §4.5.2 labels it {@code PasswordEncoder} — same encode/matches contract.</p>
 */
public class RegisterMemberUseCase {

    /** Minimum raw-password length (CONSTANTS §8 PASSWORD_MIN; PRD §5.1 AC-5). */
    private static final int PASSWORD_MIN = 6;

    private final MemberRepository memberRepo;
    private final PasswordHasher passwordEncoder;
    private final DomainEventPublisher publisher;

    public RegisterMemberUseCase(MemberRepository memberRepo, PasswordHasher passwordEncoder,
                                 DomainEventPublisher publisher) {
        this.memberRepo = memberRepo;
        this.passwordEncoder = passwordEncoder;
        this.publisher = publisher;
    }

    /**
     * Register a new member and publish {@code MemberRegistered} after persistence.
     * @throws IllegalArgumentException invalid email / displayName / password → 400 (no save, no event)
     * @throws EmailAlreadyExistsException email already registered → 409 (no save, no event)
     */
    public RegisterResult execute(RegisterCommand cmd) {
        Email email = Email.of(cmd.email());
        DisplayName displayName = DisplayName.of(cmd.displayName());
        if (cmd.password() == null || cmd.password().length() < PASSWORD_MIN) {
            throw new IllegalArgumentException("密碼至少 " + PASSWORD_MIN + " 碼");
        }

        if (memberRepo.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        String passwordHash = passwordEncoder.encode(cmd.password());
        AccountId accountId = memberRepo.save(Account.register(email, displayName, passwordHash));
        publisher.publish(new MemberRegistered(accountId, email.value()));

        return new RegisterResult(accountId);
    }
}
