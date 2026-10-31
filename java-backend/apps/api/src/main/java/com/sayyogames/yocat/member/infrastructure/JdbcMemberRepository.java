package com.sayyogames.yocat.member.infrastructure;

import com.sayyogames.yocat.member.domain.Account;
import com.sayyogames.yocat.member.domain.DisplayName;
import com.sayyogames.yocat.member.domain.Email;
import com.sayyogames.yocat.member.domain.MemberRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for the {@link MemberRepository} port (O-04, EDD §4.5.4.5 / §2 DIP; HC-5).
 * Implements the domain-owned port against {@code member.accounts} on the real PostgreSQL 16 schema
 * (SCHEMA §3.1.1). Spring injects the shared {@link JdbcTemplate}; the adapter maps rows to the {@link Account}
 * aggregate and exposes only domain types (no row/ORM leak). All queries are parameterised (no SQL injection),
 * and the email-uniqueness violation ({@code uq_accounts_email}) surfaces as Spring's
 * {@link org.springframework.dao.DuplicateKeyException} via {@code JdbcTemplate} exception translation.
 */
@Repository
public class JdbcMemberRepository implements MemberRepository {

    private static final RowMapper<Account> ACCOUNT_MAPPER = (rs, rowNum) -> Account.of(
            AccountId.of(rs.getLong("id")),
            Email.of(rs.getString("email")),
            DisplayName.of(rs.getString("display_name")),
            rs.getString("pass_hash"));

    private final JdbcTemplate jdbc;

    public JdbcMemberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findByEmail(Email email) {
        return jdbc.query(
                "SELECT id, email, display_name, pass_hash FROM member.accounts "
                        + "WHERE LOWER(email) = LOWER(?) AND deleted_at IS NULL",
                ACCOUNT_MAPPER, email.value()).stream().findFirst();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.query(
                "SELECT id, email, display_name, pass_hash FROM member.accounts "
                        + "WHERE id = ? AND deleted_at IS NULL",
                ACCOUNT_MAPPER, id.value()).stream().findFirst();
    }

    @Override
    public AccountId save(Account account) {
        Long id = jdbc.queryForObject(
                "INSERT INTO member.accounts(email, display_name, pass_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class, account.email().value(), account.displayName().value(), account.passwordHash());
        return AccountId.of(id);
    }

    @Override
    public boolean existsByEmail(Email email) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM member.accounts "
                        + "WHERE LOWER(email) = LOWER(?) AND deleted_at IS NULL)",
                Boolean.class, email.value());
        return Boolean.TRUE.equals(exists);
    }
}
