package com.sayyogames.yocat.gamesave.domain;

import com.sayyogames.yocat.shared.AccountId;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — game-save BC cross-device save (EDD §3.4 / SCHEMA §3.7.1
 * {@code game_save.saves}, PK {@code (account_id, slot)}, upsert). Pure domain — no Spring, no JDBC.
 *
 * <p>Fields mirror the SCHEMA columns {@code account_id / slot / data}; {@code data} is carried as the raw
 * JSON text of the {@code JSONB} column. An instance is rehydrated / constructed through {@link #of} and
 * upserted by {@code (account_id, slot)}.</p>
 */
public class OfficeSave {

    private AccountId accountId;
    private String slot;
    private String data;

    protected OfficeSave() {
    }

    private OfficeSave(AccountId accountId, String slot, String data) {
        this.accountId = accountId;
        this.slot = slot;
        this.data = data;
    }

    /** Builds / rehydrates a save for {@code (account, slot)} (repository rehydration and PutSaveUseCase). */
    public static OfficeSave of(AccountId accountId, String slot, String data) {
        return new OfficeSave(accountId, slot, data);
    }

    public AccountId accountId() {
        return accountId;
    }

    /** Save slot (default {@code main}, CONSTANTS §7). */
    public String slot() {
        return slot;
    }

    /** Raw JSON payload of the {@code JSONB} column. */
    public String data() {
        return data;
    }
}
