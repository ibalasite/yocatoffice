package com.sayyogames.yocat.record.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.time.Instant;
import java.util.List;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link BusinessRecord} aggregate (record BC).
 *
 * <p>Domain-owned interface; infrastructure adapter injected via Spring DI (DIP).</p>
 */
public interface BusinessRecordRepository {

    /** Persist a new business record. Caller: AddRecordUseCase (POST /records, API §1061). */
    BusinessRecord save(BusinessRecord record);

    /**
     * One page of the account's records, newest first ({@code created_at DESC}, keyset/cursor pagination,
     * SCHEMA {@code idx_records_acct}). Pass {@code null} cursor arguments for the first page; {@code limit}
     * is clamped to the fixed upper bound of 100 (API §329). Caller: ListRecordsUseCase (GET /records).
     */
    List<BusinessRecord> findByAccountNewestFirst(AccountId accountId, Instant beforeCreatedAt, Long beforeId, int limit);
}
