package com.sayyogames.yocat.deposit.domain;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — deposit BC top-up pack catalog entry (EDD §3.4 / SCHEMA §3.3.1
 * {@code deposit.deposit_pack}, PK {@code pack_id}). A bounded, seeded reference catalog entry that is
 * read-only at runtime (CONSTANTS §7 / API {@code GET /deposit/packs}). Pure domain — no Spring, no JDBC.
 *
 * <p>Fields mirror the SCHEMA columns {@code pack_id / currency / amount / label / bonus_pct / is_active}
 * verbatim; the repository adapter rehydrates an instance through {@link #of} and exposes only this domain
 * type (no leak of row/ORM detail).</p>
 */
public class DepositPack {

    private String packId;
    private String currency;
    private long amount;
    private String label;
    private Integer bonusPct;
    private boolean active;

    protected DepositPack() {
    }

    private DepositPack(String packId, String currency, long amount, String label, Integer bonusPct, boolean active) {
        this.packId = packId;
        this.currency = currency;
        this.amount = amount;
        this.label = label;
        this.bonusPct = bonusPct;
        this.active = active;
    }

    /** Reconstitutes a catalog pack from its persisted columns (repository rehydration, SCHEMA §3.3.1). */
    public static DepositPack of(String packId, String currency, long amount, String label,
                                 Integer bonusPct, boolean active) {
        return new DepositPack(packId, currency, amount, label, bonusPct, active);
    }

    public String packId() {
        return packId;
    }

    /** Pack currency — {@code gems} or {@code cash} (SCHEMA CHECK). */
    public String currency() {
        return currency;
    }

    /** Credited amount of {@code currency} for this pack (SCHEMA {@code amount > 0}). */
    public long amount() {
        return amount;
    }

    public String label() {
        return label;
    }

    /** Optional bonus percentage in {@code [0,100]}; {@code null} when the pack grants no bonus. */
    public Integer bonusPct() {
        return bonusPct;
    }

    /** Whether the pack is currently offered (only active packs surface in {@code findAllActive}). */
    public boolean active() {
        return active;
    }
}
