package com.sayyogames.yocat.deposit.domain;

import java.util.List;
import java.util.Optional;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link DepositPack} catalog (deposit BC).
 *
 * <p>Domain-owned interface; infrastructure adapter injected via Spring DI (DIP). {@code deposit.deposit_pack}
 * is a bounded, seeded reference catalog (SCHEMA §deposit) and is read-only at runtime (deposit_order is Phase B).</p>
 */
public interface DepositPackRepository {

    /** The bounded active top-up pack catalog for display (small fixed reference set, CONSTANTS §7). Caller: ListPacksUseCase (GET /deposit/packs, API §835). */
    List<DepositPack> findAllActive();

    /** Resolve a pack by its business key for checkout/topup validation; empty when the id is not in the catalog. Caller: CheckoutUseCase / topup pack resolution (API §878). */
    Optional<DepositPack> findByPackId(String packId);
}
