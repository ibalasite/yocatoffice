package com.sayyogames.yocat.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sayyogames.yocat.persistence.support.AbstractSchemaIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * DB-contract integration tests for the {@code entitlement} BC schema (SCHEMA §3.4): the idempotent-buy
 * UNIQUE guard {@code (account_id, kind, item_id) WHERE deleted_at IS NULL} that maps duplicate purchases
 * to 409, and the same-BC {@code item_id} FK to {@code entitlement_catalog} with the default RESTRICT
 * semantics (test-plan §3.2 — entitlements UNIQUE 防重複購買 / Review #3).
 */
class EntitlementSchemaIT extends AbstractSchemaIT {

    private long acct;

    @BeforeEach
    void seedCatalog() {
        acct = insertAccount("ent@example.com", "Ent Co");
        jdbc.update("INSERT INTO entitlement.entitlement_catalog(item_id, kind, label, price_currency, price_amount) "
                + "VALUES ('hat_gold', 'cosmetic', 'Gold Hat', 'gems', 100)");
    }

    @Test
    @DisplayName("uq_entitlement_acct_kind_item rejects buying the same (kind,item_id) twice for one account")
    void duplicateOwnershipIsRejected() {
        jdbc.update("INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'cosmetic', 'hat_gold')",
                acct);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'cosmetic', 'hat_gold')",
                acct))
                .isInstanceOf(DuplicateKeyException.class);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM entitlement.entitlements WHERE account_id = ? AND deleted_at IS NULL",
                Integer.class, acct);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("the idempotent-buy UNIQUE guard is partial: a soft-deleted entitlement can be re-acquired")
    void softDeletedEntitlementCanBeReacquired() {
        jdbc.update("INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'cosmetic', 'hat_gold')",
                acct);
        jdbc.update("UPDATE entitlement.entitlements SET deleted_at = now() WHERE account_id = ?", acct);

        jdbc.update("INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'cosmetic', 'hat_gold')",
                acct);

        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM entitlement.entitlements WHERE account_id = ? AND deleted_at IS NULL",
                Integer.class, acct);
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("entitlements.item_id FK to entitlement_catalog rejects a grant of a non-catalog item")
    void grantingNonCatalogItemViolatesForeignKey() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'dream', 'ghost_item')",
                acct))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RESTRICT FK blocks deleting a catalog item that an entitlement still references")
    void deletingReferencedCatalogItemIsRestricted() {
        jdbc.update("INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, 'cosmetic', 'hat_gold')",
                acct);

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM entitlement.entitlement_catalog WHERE item_id = 'hat_gold'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
