package com.sayyogames.yocat.entitlement.infrastructure;

import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.entitlement.domain.EntitlementKind;
import com.sayyogames.yocat.entitlement.domain.EntitlementRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for the {@link EntitlementRepository} port (O-04, EDD §4.5.4.3; §2 DIP).
 * Implements the domain-owned port against {@code entitlement.entitlements} on the real PostgreSQL 16 schema
 * (SCHEMA §3.4.1, {@code UNIQUE(account_id, kind, item_id) WHERE deleted_at IS NULL}).
 *
 * <p>{@link #existsOwned} is the idempotent-buy guard (→ 409). {@link #save} inserts a new grant
 * ({@code id == 0}) and returns it with its assigned {@code BIGSERIAL} identity; an already-persisted grant
 * ({@code id > 0}, e.g. a rehydrated aggregate) is returned unchanged, since an owned entitlement is immutable
 * (破產不消失, PRD §5.3). The uniqueness violation surfaces as Spring's
 * {@link org.springframework.dao.DuplicateKeyException}. The {@code entitlement.kind_enum} column is bound via
 * an explicit {@code ?::entitlement.kind_enum} cast; all statements are parameterised.</p>
 */
@Repository
public class JdbcEntitlementRepository implements EntitlementRepository {

    private static final RowMapper<Entitlement> ENTITLEMENT_MAPPER = (rs, rowNum) -> {
        Timestamp acquired = rs.getTimestamp("acquired_at");
        return Entitlement.of(
                rs.getLong("id"),
                AccountId.of(rs.getLong("account_id")),
                EntitlementKind.valueOf(rs.getString("kind").toUpperCase(Locale.ROOT)),
                rs.getString("item_id"),
                acquired == null ? null : acquired.toInstant());
    };

    private final JdbcTemplate jdbc;

    public JdbcEntitlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsOwned(AccountId accountId, EntitlementKind kind, String itemId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM entitlement.entitlements "
                        + "WHERE account_id = ? AND kind = ?::entitlement.kind_enum AND item_id = ? "
                        + "AND deleted_at IS NULL)",
                Boolean.class, accountId.value(), kindValue(kind), itemId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<Entitlement> findByAccount(AccountId accountId) {
        return jdbc.query(
                "SELECT id, account_id, kind, item_id, acquired_at FROM entitlement.entitlements "
                        + "WHERE account_id = ? AND deleted_at IS NULL ORDER BY acquired_at, id",
                ENTITLEMENT_MAPPER, accountId.value());
    }

    @Override
    public Entitlement save(Entitlement entitlement) {
        if (entitlement.id() > 0) {
            return entitlement;
        }
        return jdbc.queryForObject(
                "INSERT INTO entitlement.entitlements(account_id, kind, item_id) "
                        + "VALUES (?, ?::entitlement.kind_enum, ?) RETURNING id, account_id, kind, item_id, acquired_at",
                ENTITLEMENT_MAPPER,
                entitlement.accountId().value(), kindValue(entitlement.kind()), entitlement.itemId());
    }

    private static String kindValue(EntitlementKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
