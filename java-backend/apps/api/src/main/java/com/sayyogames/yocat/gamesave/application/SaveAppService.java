package com.sayyogames.yocat.gamesave.application;

import com.sayyogames.yocat.gamesave.domain.OfficeSave;
import com.sayyogames.yocat.gamesave.domain.OfficeSaveRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; game-save BC cross-device sync (EDD §3.4 / API §3 game-save: {@code PUT /save},
 * {@code GET /save}). Upsert by {@code (account_id, slot)} so the latest write wins across devices; a query of
 * an empty slot returns {@code Optional.empty()} (rendered as {@code {"save": null}} with 200, never 404).
 * Validation of the required {@code data} field is enforced at the presentation boundary.
 */
@Service
public class SaveAppService {

    private final OfficeSaveRepository saveRepo;

    public SaveAppService(OfficeSaveRepository saveRepo) {
        this.saveRepo = saveRepo;
    }

    @Transactional
    public void put(long accountId, String slot, String dataJson) {
        saveRepo.save(OfficeSave.of(AccountId.of(accountId), slot, dataJson));
    }

    @Transactional(readOnly = true)
    public Optional<OfficeSave> get(long accountId, String slot) {
        return saveRepo.findByAccountAndSlot(AccountId.of(accountId), slot);
    }
}
