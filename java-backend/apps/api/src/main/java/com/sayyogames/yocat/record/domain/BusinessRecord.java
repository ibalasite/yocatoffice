package com.sayyogames.yocat.record.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.time.Instant;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — record BC business-history entry (EDD §3.4 / SCHEMA §3.5.1
 * {@code record.records}, append-only). Pure domain — no Spring, no JDBC.
 *
 * <p>Fields mirror the SCHEMA columns {@code id / account_id / type / payload / created_at}; {@code payload}
 * is carried as the raw JSON text of the {@code JSONB} column. A new entry ({@link #create}) carries
 * {@code id == 0}; the repository assigns the {@code BIGSERIAL} identity on {@code save}. A persisted entry
 * is rehydrated through {@link #of}.</p>
 */
public class BusinessRecord {

    private long id;
    private AccountId accountId;
    private String type;
    private String payload;
    private Instant createdAt;

    protected BusinessRecord() {
    }

    private BusinessRecord(long id, AccountId accountId, String type, String payload, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /** A new, not-yet-persisted business record ({@code id} assigned by the repository). Caller: AddRecordUseCase. */
    public static BusinessRecord create(AccountId accountId, String type, String payload) {
        return new BusinessRecord(0L, accountId, type, payload, null);
    }

    /** Reconstitutes a persisted business record from its columns (repository rehydration, SCHEMA §3.5.1). */
    public static BusinessRecord of(long id, AccountId accountId, String type, String payload, Instant createdAt) {
        return new BusinessRecord(id, accountId, type, payload, createdAt);
    }

    public long id() {
        return id;
    }

    public AccountId accountId() {
        return accountId;
    }

    /** Record type — {@code bankruptcy / achievement / legacy} (CONSTANTS §9). */
    public String type() {
        return type;
    }

    /** Raw JSON payload of the {@code JSONB} column. */
    public String payload() {
        return payload;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
