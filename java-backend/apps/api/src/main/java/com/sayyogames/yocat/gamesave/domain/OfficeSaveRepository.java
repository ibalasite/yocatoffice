package com.sayyogames.yocat.gamesave.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link OfficeSave} aggregate (game-save BC).
 *
 * <p>Domain-owned interface; infrastructure adapter injected via Spring DI (DIP).</p>
 */
public interface OfficeSaveRepository {

    /**
     * Load the save for {@code (account, slot)}; empty when none exists
     * (GET /save → {@code {"save": null}}, API §1257 / SCHEMA PK {@code (account_id, slot)}). Caller: GetSaveUseCase.
     */
    Optional<OfficeSave> findByAccountAndSlot(AccountId accountId, String slot);

    /** Upsert the office save by {@code (account_id, slot)} ({@code ON CONFLICT DO UPDATE}, API §1469). Caller: PutSaveUseCase (PUT /save). */
    OfficeSave save(OfficeSave officeSave);
}
