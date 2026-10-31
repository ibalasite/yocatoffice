package com.sayyogames.yocat.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.persistence.support.AbstractRepositoryIT;
import com.sayyogames.yocat.record.domain.BusinessRecord;
import com.sayyogames.yocat.record.domain.BusinessRecordRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RED integration test for the {@code JdbcBusinessRecordRepository} adapter behind
 * {@link BusinessRecordRepository} (O-04, API §records). Covers every port method against the real
 * PostgreSQL 16 container — {@code findByAccountNewestFirst} (keyset pagination, newest first) and
 * {@code save} (exercised via a rehydrated aggregate). The adapter bean does not exist until
 * {@code persist-repo-impls}, so the suite is RED (Review #11).
 */
class JdbcBusinessRecordRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private BusinessRecordRepository businessRecordRepository;

    private AccountId acct;

    @BeforeEach
    void seedRecords() {
        long id = seedAccount("rec-repo@example.com", "Record Owner");
        acct = AccountId.of(id);
        jdbc.update("INSERT INTO record.record_type_catalog(type_code, label) VALUES ('achievement', 'Achievement')");
        jdbc.update("INSERT INTO record.record_type_catalog(type_code, label) VALUES ('bankruptcy', 'Bankruptcy')");
        jdbc.update("INSERT INTO record.records(account_id, type, payload, created_at) "
                + "VALUES (?, 'achievement', '{\"n\":1}', now() - interval '2 minutes')", id);
        jdbc.update("INSERT INTO record.records(account_id, type, payload, created_at) "
                + "VALUES (?, 'bankruptcy', '{\"n\":2}', now() - interval '1 minute')", id);
        jdbc.update("INSERT INTO record.records(account_id, type, payload, created_at) "
                + "VALUES (?, 'achievement', '{\"n\":3}', now())", id);
    }

    @Test
    @DisplayName("findByAccountNewestFirst returns the first page newest-first, clamped to the limit")
    void findByAccountNewestFirstReturnsLimitedPage() {
        List<BusinessRecord> page = businessRecordRepository.findByAccountNewestFirst(acct, null, null, 2);

        assertThat(page).hasSize(2);
        assertThat(page).allMatch(r -> acct.equals(r.accountId()));
        assertThat(page.get(0).id()).isGreaterThan(page.get(1).id());
    }

    @Test
    @DisplayName("save re-persists a rehydrated business record and returns it with its identity")
    void saveRoundTripsRehydratedRecord() {
        BusinessRecord loaded = businessRecordRepository.findByAccountNewestFirst(acct, null, null, 1).get(0);

        BusinessRecord saved = businessRecordRepository.save(loaded);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isPositive();
    }
}
