package com.sayyogames.yocat.gamesave.infrastructure;

import com.sayyogames.yocat.gamesave.domain.OfficeSave;
import com.sayyogames.yocat.gamesave.domain.OfficeSaveRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for the {@link OfficeSaveRepository} port (O-04, API {@code GET/PUT /save};
 * §2 DIP). Implements the domain-owned port against {@code game_save.saves} on the real PostgreSQL 16 schema
 * (SCHEMA §3.7.1, PK {@code (account_id, slot)}).
 *
 * <p>{@link #findByAccountAndSlot} loads the save for a {@code (account, slot)} (empty → {@code GET /save}
 * returns {@code {"save": null}}). {@link #save} upserts with
 * {@code ON CONFLICT (account_id, slot) DO UPDATE}, keeping a single row per slot (cross-device last-write).
 * The {@code JSONB} column is bound via an explicit {@code ?::jsonb} cast; all statements are parameterised.</p>
 */
@Repository
public class JdbcOfficeSaveRepository implements OfficeSaveRepository {

    private static final RowMapper<OfficeSave> SAVE_MAPPER = (rs, rowNum) -> OfficeSave.of(
            AccountId.of(rs.getLong("account_id")),
            rs.getString("slot"),
            rs.getString("data"));

    private final JdbcTemplate jdbc;

    public JdbcOfficeSaveRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OfficeSave> findByAccountAndSlot(AccountId accountId, String slot) {
        return jdbc.query(
                "SELECT account_id, slot, data FROM game_save.saves WHERE account_id = ? AND slot = ?",
                SAVE_MAPPER, accountId.value(), slot).stream().findFirst();
    }

    @Override
    public OfficeSave save(OfficeSave officeSave) {
        jdbc.update(
                "INSERT INTO game_save.saves(account_id, slot, data) VALUES (?, ?, ?::jsonb) "
                        + "ON CONFLICT (account_id, slot) DO UPDATE SET data = EXCLUDED.data",
                officeSave.accountId().value(), officeSave.slot(), officeSave.data());
        return officeSave;
    }
}
