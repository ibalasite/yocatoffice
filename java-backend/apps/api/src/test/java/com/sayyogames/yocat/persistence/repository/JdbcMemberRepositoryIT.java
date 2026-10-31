package com.sayyogames.yocat.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sayyogames.yocat.member.domain.Account;
import com.sayyogames.yocat.member.domain.DisplayName;
import com.sayyogames.yocat.member.domain.Email;
import com.sayyogames.yocat.member.domain.MemberRepository;
import com.sayyogames.yocat.persistence.support.AbstractRepositoryIT;
import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/**
 * RED integration test for the {@code JdbcMemberRepository} adapter behind {@link MemberRepository}
 * (O-04, EDD §4.5.4.5). Covers every port method against the real PostgreSQL 16 container:
 * {@code save}, {@code findByEmail}, {@code findById}, {@code existsByEmail}. The adapter bean does not
 * exist until {@code persist-repo-impls}; the {@code @Autowired} port therefore fails to resolve and the
 * suite is RED (Review #11).
 */
class JdbcMemberRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @org.junit.jupiter.api.DisplayName("save persists a new account and it is retrievable by email and by id")
    void saveThenFindByEmailAndById() {
        Account account = Account.register(Email.of("alice@example.com"), DisplayName.of("Alice Co"), "salt$hash");

        AccountId id = memberRepository.save(account);

        assertThat(id).isNotNull();
        Optional<Account> byEmail = memberRepository.findByEmail(Email.of("alice@example.com"));
        assertThat(byEmail).isPresent();
        assertThat(byEmail.get().email()).isEqualTo(Email.of("alice@example.com"));
        Optional<Account> byId = memberRepository.findById(id);
        assertThat(byId).isPresent();
        assertThat(byId.get().id()).isEqualTo(id);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("findByEmail normalises case and returns empty for an unknown email")
    void findByEmailIsCaseInsensitiveAndEmptyWhenAbsent() {
        memberRepository.save(Account.register(Email.of("Bob@Example.com"), DisplayName.of("Bob Co"), "salt$hash"));

        assertThat(memberRepository.findByEmail(Email.of("bob@example.com"))).isPresent();
        assertThat(memberRepository.findByEmail(Email.of("missing@example.com"))).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("existsByEmail reflects persistence state before and after save")
    void existsByEmailReflectsPersistence() {
        Email email = Email.of("carol@example.com");
        assertThat(memberRepository.existsByEmail(email)).isFalse();

        memberRepository.save(Account.register(email, DisplayName.of("Carol Co"), "salt$hash"));

        assertThat(memberRepository.existsByEmail(email)).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("save surfaces the email-uniqueness violation when the same email is registered twice")
    void duplicateEmailSaveViolatesUniqueness() {
        memberRepository.save(Account.register(Email.of("dup@example.com"), DisplayName.of("First"), "salt$hash"));

        assertThatThrownBy(() -> memberRepository.save(
                Account.register(Email.of("dup@example.com"), DisplayName.of("Second"), "salt$hash")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
