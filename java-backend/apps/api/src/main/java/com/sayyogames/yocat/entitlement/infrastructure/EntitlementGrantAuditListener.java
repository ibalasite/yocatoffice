package com.sayyogames.yocat.entitlement.infrastructure;

import com.sayyogames.yocat.entitlement.domain.EntitlementGranted;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * entitlement BC internal consumer of {@code EntitlementGranted} (EDD §4.6.1 — the event's grant-audit sink,
 * SCHEMA §3.4.3 {@code entitlement.entitlement_grant_log} "EntitlementGranted 事件來源"). Writes one audit row
 * per granted item. Being an {@link ApplicationModuleListener}, it makes the grant flow through the
 * transactional Outbox / event-publication registry (at-least-once, module-decoupled delivery).
 */
@Component
public class EntitlementGrantAuditListener {

    private final JdbcTemplate jdbc;

    public EntitlementGrantAuditListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @ApplicationModuleListener
    public void onEntitlementGranted(EntitlementGranted event) {
        jdbc.update(
                "INSERT INTO entitlement.entitlement_grant_log(account_id, kind, item_id, charged_amount) "
                        + "VALUES (?, ?::entitlement.kind_enum, ?, 0)",
                event.accountId().value(), event.kind().name().toLowerCase(Locale.ROOT), event.itemId());
    }
}
