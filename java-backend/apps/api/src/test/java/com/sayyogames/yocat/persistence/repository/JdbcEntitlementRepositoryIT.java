package com.sayyogames.yocat.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.entitlement.domain.EntitlementKind;
import com.sayyogames.yocat.entitlement.domain.EntitlementRepository;
import com.sayyogames.yocat.persistence.support.AbstractRepositoryIT;
import com.sayyogames.yocat.shared.AccountId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RED integration test for the {@code JdbcEntitlementRepository} adapter behind
 * {@link EntitlementRepository} (O-04, EDD §4.5.4.3). Covers every port method against the real
 * PostgreSQL 16 container — {@code existsOwned} (the idempotent-buy guard), {@code findByAccount}, and
 * {@code save} (exercised by re-persisting an aggregate rehydrated from a seeded row, which needs no
 * not-yet-defined domain constructor). The adapter bean does not exist until {@code persist-repo-impls},
 * so the suite is RED (Review #11).
 */
class JdbcEntitlementRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private EntitlementRepository entitlementRepository;

    private AccountId acct;

    @BeforeEach
    void seedEntitlements() {
        long id = seedAccount("ent-repo@example.com", "Ent Owner");
        acct = AccountId.of(id);
        jdbc.update("INSERT INTO entitlement.entitlement_catalog(item_id, kind, label, price_currency, price_amount) "
                + "VALUES ('hat_gold', 'cosmetic', 'Gold Hat', 'gems', 100)");
        jdbc.update("INSERT INTO entitlement.entitlement_catalog(item_id, kind, label, price_currency, price_amount) "
                + "VALUES ('dream_villa', 'dream', 'Dream Villa', 'gems', 900)");
        jdbc.update("INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'cosmetic', 'hat_gold')",
                id);
        jdbc.update("INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'dream', 'dream_villa')",
                id);
    }

    @Test
    @DisplayName("existsOwned is true for an owned (kind,item_id) and false otherwise")
    void existsOwnedReflectsOwnership() {
        assertThat(entitlementRepository.existsOwned(acct, EntitlementKind.COSMETIC, "hat_gold")).isTrue();
        assertThat(entitlementRepository.existsOwned(acct, EntitlementKind.SCENARIO, "hat_gold")).isFalse();
        assertThat(entitlementRepository.existsOwned(acct, EntitlementKind.COSMETIC, "not_owned")).isFalse();
    }

    @Test
    @DisplayName("findByAccount returns all owned entitlements for the account")
    void findByAccountReturnsOwnedEntitlements() {
        List<Entitlement> owned = entitlementRepository.findByAccount(acct);

        assertThat(owned).hasSize(2);
        assertThat(owned).allMatch(e -> acct.equals(e.accountId()));
    }

    @Test
    @DisplayName("save re-persists a rehydrated entitlement aggregate and returns it with its identity")
    void saveRoundTripsRehydratedAggregate() {
        Entitlement loaded = entitlementRepository.findByAccount(acct).get(0);

        Entitlement saved = entitlementRepository.save(loaded);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isPositive();
        assertThat(entitlementRepository.findByAccount(acct)).hasSize(2);
    }
}
