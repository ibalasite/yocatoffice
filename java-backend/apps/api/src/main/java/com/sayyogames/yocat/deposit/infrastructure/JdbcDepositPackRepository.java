package com.sayyogames.yocat.deposit.infrastructure;

import com.sayyogames.yocat.deposit.domain.DepositPack;
import com.sayyogames.yocat.deposit.domain.DepositPackRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for the {@link DepositPackRepository} port (O-04, API {@code GET /deposit/packs};
 * §2 DIP). Implements the domain-owned port against the read-only, seeded catalog {@code deposit.deposit_pack}
 * on the real PostgreSQL 16 schema (SCHEMA §3.3.1). Rows map to the {@link DepositPack} domain type; all
 * statements are parameterised.
 */
@Repository
public class JdbcDepositPackRepository implements DepositPackRepository {

    private static final RowMapper<DepositPack> PACK_MAPPER = (rs, rowNum) -> DepositPack.of(
            rs.getString("pack_id"),
            rs.getString("currency"),
            rs.getLong("amount"),
            rs.getString("label"),
            (Integer) rs.getObject("bonus_pct"),
            rs.getBoolean("is_active"));

    private final JdbcTemplate jdbc;

    public JdbcDepositPackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DepositPack> findAllActive() {
        return jdbc.query(
                "SELECT pack_id, currency, amount, label, bonus_pct, is_active FROM deposit.deposit_pack "
                        + "WHERE is_active = TRUE ORDER BY pack_id",
                PACK_MAPPER);
    }

    @Override
    public Optional<DepositPack> findByPackId(String packId) {
        return jdbc.query(
                "SELECT pack_id, currency, amount, label, bonus_pct, is_active FROM deposit.deposit_pack "
                        + "WHERE pack_id = ?",
                PACK_MAPPER, packId).stream().findFirst();
    }
}
