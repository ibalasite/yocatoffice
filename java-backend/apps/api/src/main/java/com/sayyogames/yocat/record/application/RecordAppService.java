package com.sayyogames.yocat.record.application;

import com.sayyogames.yocat.record.domain.BusinessRecord;
import com.sayyogames.yocat.record.domain.BusinessRecordRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; record BC append-only business history (EDD §3.4 / API §3 record:
 * {@code POST /records}, {@code GET /records}). Validates the record type against the CONSTANTS §9 set
 * ({@code bankruptcy/achievement/legacy}; anything else → 400 {@code "type 不正確"}), writes the record
 * against the authenticated account, and lists newest-first with an optional type filter (page capped at 100).
 */
@Service
public class RecordAppService {

    /** Valid record types (CONSTANTS §9 / SCHEMA record.type_enum). */
    private static final Set<String> VALID_TYPES = Set.of("bankruptcy", "achievement", "legacy");
    private static final int PAGE_CAP = 100;

    private final BusinessRecordRepository recordRepo;

    public RecordAppService(BusinessRecordRepository recordRepo) {
        this.recordRepo = recordRepo;
    }

    @Transactional
    public void add(long accountId, String type, String payloadJson) {
        String normalised = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (!VALID_TYPES.contains(normalised)) {
            throw new IllegalArgumentException("type 不正確");
        }
        String payload = (payloadJson == null || payloadJson.isBlank()) ? "{}" : payloadJson;
        recordRepo.save(BusinessRecord.create(AccountId.of(accountId), normalised, payload));
    }

    @Transactional(readOnly = true)
    public List<BusinessRecord> list(long accountId, String typeFilter) {
        List<BusinessRecord> page = recordRepo.findByAccountNewestFirst(AccountId.of(accountId), null, null, PAGE_CAP);
        if (typeFilter == null || typeFilter.isBlank()) {
            return page;
        }
        String wanted = typeFilter.toLowerCase(Locale.ROOT);
        return page.stream().filter(r -> wanted.equals(r.type())).toList();
    }
}
