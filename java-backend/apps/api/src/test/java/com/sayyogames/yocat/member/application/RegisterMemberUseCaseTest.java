package com.sayyogames.yocat.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sayyogames.yocat.member.domain.Account;
import com.sayyogames.yocat.member.domain.Email;
import com.sayyogames.yocat.shared.MemberRegistered;
import com.sayyogames.yocat.member.domain.MemberRepository;
import com.sayyogames.yocat.member.domain.PasswordHasher;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Application-layer unit test (RED) — {@link RegisterMemberUseCase}, a &lt;&lt;Command&gt;&gt; use case with a cross-BC event.
 *
 * <p>Spec: EDD §4.5.4.5 (register + MemberRegistered) / §5.4 (409 duplicate email); test-plan §3.1 Application layer.
 * Ports ({@link MemberRepository}, {@link PasswordHasher}, {@link DomainEventPublisher}) are mocked.
 * Traces PRD AC RQ-001 / US-MEM-001 (TC-UNIT-REGMEMBER-S01, TC-UNIT-MEM-003-B01 field validation).</p>
 *
 * <p>RED: {@code RegisterMemberUseCase.execute} is an un-wired skeleton (orchestrate-usecases, GREEN).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterMemberUseCaseTest {

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private PasswordHasher passwordEncoder;

    @Mock
    private DomainEventPublisher publisher;

    private RegisterMemberUseCase useCase() {
        return new RegisterMemberUseCase(memberRepo, passwordEncoder, publisher);
    }

    @Test
    @DisplayName("TC-UNIT-REGMEMBER-S01: valid data creates account and publishes MemberRegistered")
    void register_success_persists_and_publishes_event() {
        when(memberRepo.existsByEmail(Email.of("alice@yocat.com"))).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("salt$hash");
        when(memberRepo.save(any(Account.class))).thenReturn(AccountId.of(1001L));

        RegisterResult result = useCase().execute(new RegisterCommand("alice@yocat.com", "Alice Corp", "secret1"));

        assertThat(result.accountId()).isEqualTo(AccountId.of(1001L));
        verify(memberRepo).save(any(Account.class));
        // Event published after persistence so the wallet listener can create the init ledger (EDD §4.5.4.5).
        verify(publisher).publish(any(MemberRegistered.class));
    }

    @Test
    @DisplayName("TC-UNIT-REGMEMBER-E01: duplicate email → EmailAlreadyExistsException (409), no save, no event")
    void register_duplicate_email_conflicts_without_side_effects() {
        when(memberRepo.existsByEmail(Email.of("alice@yocat.com"))).thenReturn(true);

        assertThatThrownBy(() -> useCase().execute(new RegisterCommand("alice@yocat.com", "Alice Corp", "secret1")))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(memberRepo, never()).save(any(Account.class));
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-UNIT-MEM-003-B01: password shorter than 6 chars → IllegalArgumentException (400), no save")
    void register_short_password_rejected() {
        assertThatThrownBy(() -> useCase().execute(new RegisterCommand("alice@yocat.com", "Alice Corp", "a1")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(memberRepo, never()).save(any(Account.class));
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-UNIT-MEM-003-B02: display name exceeding 24 chars → IllegalArgumentException (400), no save")
    void register_over_length_display_name_rejected() {
        String tooLong = "X".repeat(25);

        assertThatThrownBy(() -> useCase().execute(new RegisterCommand("alice@yocat.com", tooLong, "secret1")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(memberRepo, never()).save(any(Account.class));
    }
}
