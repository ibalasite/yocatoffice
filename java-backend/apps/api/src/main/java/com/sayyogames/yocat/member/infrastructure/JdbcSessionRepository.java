package com.sayyogames.yocat.member.infrastructure;

import com.sayyogames.yocat.member.domain.Session;
import com.sayyogames.yocat.member.domain.SessionRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for {@link SessionRepository} (EDD §4.2 / §5.1 opaque session; §2 DIP).
 * Persists against {@code member.sessions} on the real PostgreSQL 16 schema (SCHEMA §3.1.2). All statements
 * are parameterised; the adapter maps rows to the domain {@link Session} and leaks no row/ORM detail.
 */
@Repository
public class JdbcSessionRepository implements SessionRepository {

    private static final RowMapper<Session> SESSION_MAPPER = (rs, rowNum) -> Session.of(
            rs.getString("token"),
            AccountId.of(rs.getLong("account_id")),
            rs.getTimestamp("expires_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Session session) {
        jdbc.update(
                "INSERT INTO member.sessions(token, account_id, expires_at) VALUES (?, ?, ?)",
                session.token(), session.accountId().value(), java.sql.Timestamp.from(session.expiresAt()));
    }

    @Override
    public Optional<Session> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                "SELECT token, account_id, expires_at FROM member.sessions WHERE token = ?",
                SESSION_MAPPER, token).stream().findFirst();
    }

    @Override
    public void deleteByToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        jdbc.update("DELETE FROM member.sessions WHERE token = ?", token);
    }
}
