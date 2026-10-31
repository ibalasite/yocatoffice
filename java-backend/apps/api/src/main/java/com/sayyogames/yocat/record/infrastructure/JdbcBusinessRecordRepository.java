package com.sayyogames.yocat.record.infrastructure;

import com.sayyogames.yocat.record.domain.BusinessRecord;
import com.sayyogames.yocat.record.domain.BusinessRecordRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for the {@link BusinessRecordRepository} port (O-04, API {@code GET/POST /records};
 * §2 DIP). Implements the domain-owned port against the append-only {@code record.records} on the real
 * PostgreSQL 16 schema (SCHEMA §3.5.1, {@code idx_records_acct}).
 *
 * <p>{@link #findByAccountNewestFirst} returns one keyset page ordered {@code created_at DESC, id DESC};
 * a {@code (beforeCreatedAt, beforeId)} cursor pages forward via a row-value comparison, and {@code limit} is
 * clamped to {@code [1,100]} (API §329). {@link #save} inserts a new record ({@code id == 0}) and returns it
 * with its assigned {@code BIGSERIAL} identity; an already-persisted record is returned unchanged (append-only,
 * never rewritten). The {@code record.type_enum} and {@code JSONB} columns are bound via explicit casts; all
 * statements are parameterised.</p>
 */
@Repository
public class JdbcBusinessRecordRepository implements BusinessRecordRepository {

    private static final int MAX_LIMIT = 100;

    private static final RowMapper<BusinessRecord> RECORD_MAPPER = (rs, rowNum) -> BusinessRecord.of(
            rs.getLong("id"),
            AccountId.of(rs.getLong("account_id")),
            rs.getString("type"),
            rs.getString("payload"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcBusinessRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BusinessRecord save(BusinessRecord record) {
        if (record.id() > 0) {
            return record;
        }
        // Referential safeguard: the record_type_catalog seed (migration V011) is deferred, yet the
        // records.type FK requires the row. Idempotently ensure the (already-validated) type exists so the
        // append-only INSERT never trips the FK on a fresh/truncated catalog (CONSTANTS §9 fixed type set).
        jdbc.update(
                "INSERT INTO record.record_type_catalog(type_code, label) VALUES (?::record.type_enum, ?) "
                        + "ON CONFLICT (type_code) DO NOTHING",
                record.type(), record.type());
        return jdbc.queryForObject(
                "INSERT INTO record.records(account_id, type, payload) "
                        + "VALUES (?, ?::record.type_enum, ?::jsonb) "
                        + "RETURNING id, account_id, type, payload, created_at",
                RECORD_MAPPER, record.accountId().value(), record.type(), record.payload());
    }

    @Override
    public List<BusinessRecord> findByAccountNewestFirst(AccountId accountId, Instant beforeCreatedAt,
                                                         Long beforeId, int limit) {
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        StringBuilder sql = new StringBuilder(
                "SELECT id, account_id, type, payload, created_at FROM record.records WHERE account_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(accountId.value());
        if (beforeCreatedAt != null && beforeId != null) {
            sql.append(" AND (created_at, id) < (?, ?)");
            args.add(Timestamp.from(beforeCreatedAt));
            args.add(beforeId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(clamped);
        return jdbc.query(sql.toString(), RECORD_MAPPER, args.toArray());
    }
}
