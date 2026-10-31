package com.sayyogames.yocat.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sayyogames.yocat.persistence.support.AbstractSchemaIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * DB-contract integration tests for the {@code member} BC schema (SCHEMA §3.1): the partial
 * case-insensitive email UNIQUE index, the {@code display_name} length CHECK, and the same-BC
 * {@code ON DELETE CASCADE} FKs (test-plan §3.2 — UNIQUE 約束 / Review #3 / #4).
 */
class MemberSchemaIT extends AbstractSchemaIT {

    @Test
    @DisplayName("uq_accounts_email rejects a second active account with the same email (case-insensitive)")
    void emailUniqueIsCaseInsensitiveAmongActiveAccounts() {
        insertAccount("alice@example.com", "Alice Co");

        assertThatThrownBy(() -> insertAccount("ALICE@Example.com", "Alice Clone"))
                .isInstanceOf(DuplicateKeyException.class);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM member.accounts WHERE lower(email) = 'alice@example.com'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("uq_accounts_email is partial on deleted_at IS NULL: a soft-deleted email can be re-registered")
    void softDeletedEmailCanBeReused() {
        long id = insertAccount("bob@example.com", "Bob Co");
        jdbc.update("UPDATE member.accounts SET deleted_at = now() WHERE id = ?", id);

        long reused = insertAccount("bob@example.com", "Bob Returns");

        assertThat(reused).isNotEqualTo(id);
        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM member.accounts WHERE lower(email) = 'bob@example.com' AND deleted_at IS NULL",
                Integer.class);
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("display_name CHECK rejects a name longer than 24 characters")
    void displayNameLengthCheckRejectsOversizedName() {
        String tooLong = "x".repeat(25);

        assertThatThrownBy(() -> insertAccount("carol@example.com", tooLong))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ON DELETE CASCADE removes deletion requests and password history when the account is deleted")
    void deletingAccountCascadesToChildRows() {
        long id = insertAccount("dora@example.com", "Dora Co");
        jdbc.update("INSERT INTO member.account_deletion_requests(account_id) VALUES (?)", id);
        jdbc.update("INSERT INTO member.password_history(account_id, pass_hash) VALUES (?, ?)", id, "salt$old");

        jdbc.update("DELETE FROM member.accounts WHERE id = ?", id);

        Integer requests = jdbc.queryForObject(
                "SELECT count(*) FROM member.account_deletion_requests WHERE account_id = ?", Integer.class, id);
        Integer history = jdbc.queryForObject(
                "SELECT count(*) FROM member.password_history WHERE account_id = ?", Integer.class, id);
        assertThat(requests).isZero();
        assertThat(history).isZero();
    }
}
