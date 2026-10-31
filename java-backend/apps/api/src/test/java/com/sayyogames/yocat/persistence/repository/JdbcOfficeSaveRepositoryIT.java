package com.sayyogames.yocat.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.gamesave.domain.OfficeSave;
import com.sayyogames.yocat.gamesave.domain.OfficeSaveRepository;
import com.sayyogames.yocat.persistence.support.AbstractRepositoryIT;
import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RED integration test for the {@code JdbcOfficeSaveRepository} adapter behind
 * {@link OfficeSaveRepository} (O-04, API §save). Covers every port method against the real PostgreSQL 16
 * container — {@code findByAccountAndSlot} and {@code save} ({@code ON CONFLICT (account_id, slot) DO
 * UPDATE}, exercised via a rehydrated aggregate). The adapter bean does not exist until
 * {@code persist-repo-impls}, so the suite is RED (Review #11).
 */
class JdbcOfficeSaveRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private OfficeSaveRepository officeSaveRepository;

    private AccountId acct;

    @BeforeEach
    void seedSave() {
        long id = seedAccount("save-repo@example.com", "Save Owner");
        acct = AccountId.of(id);
        jdbc.update("INSERT INTO game_save.saves(account_id, slot, data) VALUES (?, 'main', '{\"v\":1}')", id);
    }

    @Test
    @DisplayName("findByAccountAndSlot returns the save for a known (account,slot) and is empty otherwise")
    void findByAccountAndSlotResolvesOrEmpty() {
        Optional<OfficeSave> found = officeSaveRepository.findByAccountAndSlot(acct, "main");
        assertThat(found).isPresent();
        assertThat(found.get().slot()).isEqualTo("main");

        assertThat(officeSaveRepository.findByAccountAndSlot(acct, "backup")).isEmpty();
    }

    @Test
    @DisplayName("save upserts by (account_id, slot) and keeps a single row for the slot")
    void saveUpsertsKeepingSingleRowPerSlot() {
        OfficeSave loaded = officeSaveRepository.findByAccountAndSlot(acct, "main").orElseThrow();

        OfficeSave saved = officeSaveRepository.save(loaded);

        assertThat(saved).isNotNull();
        assertThat(saved.slot()).isEqualTo("main");
        Long rows = jdbc.queryForObject(
                "SELECT count(*) FROM game_save.saves WHERE account_id = ? AND slot = 'main'",
                Long.class, acct.value());
        assertThat(rows).isEqualTo(1);
    }
}
