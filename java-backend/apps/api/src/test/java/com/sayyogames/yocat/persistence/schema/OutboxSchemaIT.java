package com.sayyogames.yocat.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sayyogames.yocat.persistence.support.AbstractSchemaIT;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * DB-contract integration tests for the Spring Modulith transactional Outbox schema (SCHEMA §3.8,
 * {@code modulith} cross-cutting schema): the Event Publication Registry, its incomplete-event partial
 * index ({@code completion_date IS NULL}), event-id de-duplication (PK), and the dead-letter retry-count
 * CHECK (test-plan §3.2 Message / Event — Outbox 投遞 / event_id 去重冪等 / EDD §4.6).
 */
class OutboxSchemaIT extends AbstractSchemaIT {

    @Test
    @DisplayName("event_publication persists a pending event and the incomplete-event query finds only uncompleted rows")
    void incompleteEventsAreVisibleUntilCompleted() {
        UUID eventId = UUID.randomUUID();
        jdbc.update("INSERT INTO modulith.event_publication(id, listener_id, event_type, serialized_event) "
                + "VALUES (?, 'wallet.CreditListener', 'DepositConfirmed', '{\"amount\":100}')", eventId);

        Integer pendingBefore = jdbc.queryForObject(
                "SELECT count(*) FROM modulith.event_publication WHERE completion_date IS NULL", Integer.class);
        assertThat(pendingBefore).isEqualTo(1);

        jdbc.update("UPDATE modulith.event_publication SET completion_date = now() WHERE id = ?", eventId);

        Integer pendingAfter = jdbc.queryForObject(
                "SELECT count(*) FROM modulith.event_publication WHERE completion_date IS NULL", Integer.class);
        assertThat(pendingAfter).isZero();
    }

    @Test
    @DisplayName("event_publication PK de-duplicates a redelivered event id (exactly-once registry key)")
    void redeliveredEventIdIsRejectedByPrimaryKey() {
        UUID eventId = UUID.randomUUID();
        jdbc.update("INSERT INTO modulith.event_publication(id, listener_id, event_type, serialized_event) "
                + "VALUES (?, 'wallet.CreditListener', 'MemberRegistered', '{}')", eventId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO modulith.event_publication(id, listener_id, event_type, serialized_event) "
                        + "VALUES (?, 'wallet.CreditListener', 'MemberRegistered', '{}')", eventId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("event_dead_letter.retry_count CHECK (>= 0) rejects a negative retry count")
    void deadLetterRetryCountMustBeNonNegative() {
        jdbc.update("INSERT INTO modulith.event_dead_letter(event_id, topic_name, retry_count, last_error) "
                + "VALUES (?, 'wallet.credit', 3, 'timeout')", UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO modulith.event_dead_letter(event_id, topic_name, retry_count) VALUES (?, 'wallet.credit', -1)",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
