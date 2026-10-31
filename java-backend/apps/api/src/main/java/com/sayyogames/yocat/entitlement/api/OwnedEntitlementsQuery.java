package com.sayyogames.yocat.entitlement.api;

import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.entitlement.domain.EntitlementRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; entitlement BC Public NamedInterface {@code api} — read model of an account's owned
 * entitlements (EDD §4.1; API {@code GET /entitlements}, aggregated by {@code GET /auth/me}). CQRS Query: no
 * side effects. Ordered by acquisition time (PRD §5.3: bankruptcy never removes owned entitlements).
 */
@Service
public class OwnedEntitlementsQuery {

    private final EntitlementRepository entRepo;

    public OwnedEntitlementsQuery(EntitlementRepository entRepo) {
        this.entRepo = entRepo;
    }

    @Transactional(readOnly = true)
    public List<OwnedEntitlementView> list(long accountId) {
        return entRepo.findByAccount(AccountId.of(accountId)).stream()
                .map(OwnedEntitlementsQuery::toView)
                .toList();
    }

    private static OwnedEntitlementView toView(Entitlement e) {
        return new OwnedEntitlementView(
                e.kind().name().toLowerCase(Locale.ROOT),
                e.itemId(),
                e.acquiredAt() == null ? null : e.acquiredAt().toString());
    }
}
