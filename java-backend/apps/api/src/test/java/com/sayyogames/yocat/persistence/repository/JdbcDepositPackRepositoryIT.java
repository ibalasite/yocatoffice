package com.sayyogames.yocat.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.deposit.domain.DepositPack;
import com.sayyogames.yocat.deposit.domain.DepositPackRepository;
import com.sayyogames.yocat.persistence.support.AbstractRepositoryIT;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RED integration test for the {@code JdbcDepositPackRepository} adapter behind
 * {@link DepositPackRepository} (O-04, API §deposit/packs). Covers both read-only port methods against
 * the real PostgreSQL 16 container — {@code findAllActive} (only active catalog entries) and
 * {@code findByPackId}. The adapter bean does not exist until {@code persist-repo-impls}, so the suite
 * is RED (Review #11).
 */
class JdbcDepositPackRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private DepositPackRepository depositPackRepository;

    @BeforeEach
    void seedPacks() {
        jdbc.update("INSERT INTO deposit.deposit_pack(pack_id, currency, amount, label, is_active) "
                + "VALUES ('pk_small', 'gems', 100, 'Small Pack', TRUE)");
        jdbc.update("INSERT INTO deposit.deposit_pack(pack_id, currency, amount, label, is_active) "
                + "VALUES ('pk_large', 'gems', 1000, 'Large Pack', TRUE)");
        jdbc.update("INSERT INTO deposit.deposit_pack(pack_id, currency, amount, label, is_active) "
                + "VALUES ('pk_retired', 'gems', 500, 'Retired Pack', FALSE)");
    }

    @Test
    @DisplayName("findAllActive returns only the active catalog packs")
    void findAllActiveExcludesInactivePacks() {
        List<DepositPack> active = depositPackRepository.findAllActive();

        assertThat(active).hasSize(2);
        assertThat(active).extracting(DepositPack::packId)
                .containsExactlyInAnyOrder("pk_small", "pk_large");
    }

    @Test
    @DisplayName("findByPackId resolves a pack by its business key and is empty for an unknown id")
    void findByPackIdResolvesOrEmpty() {
        Optional<DepositPack> found = depositPackRepository.findByPackId("pk_small");
        assertThat(found).isPresent();
        assertThat(found.get().packId()).isEqualTo("pk_small");

        assertThat(depositPackRepository.findByPackId("pk_missing")).isEmpty();
    }
}
