package com.sayyogames.yocat.deposit.infrastructure;

import com.sayyogames.yocat.deposit.domain.DepositOrder;
import com.sayyogames.yocat.deposit.domain.DepositOrderRepository;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for {@link DepositOrderRepository} (EDD §4.5.4 deposit; §2 DIP). Persists against
 * {@code deposit.deposit_order} on the real PostgreSQL 16 schema (SCHEMA §3.3.2). Phase A orders are created as
 * {@code confirmed} (column default); the {@code uq_deposit_order_idem} UNIQUE index enforces one order per
 * Idempotency-Key. All statements are parameterised.
 */
@Repository
public class JdbcDepositOrderRepository implements DepositOrderRepository {

    private static final RowMapper<DepositOrder> ORDER_MAPPER = (rs, rowNum) -> new DepositOrder(
            rs.getString("order_id"),
            rs.getLong("account_id"),
            rs.getString("pack_id"),
            rs.getString("currency"),
            rs.getLong("amount"),
            rs.getString("status"),
            rs.getString("idempotency_key"));

    private final JdbcTemplate jdbc;

    public JdbcDepositOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DepositOrder> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query(
                "SELECT order_id, account_id, pack_id, currency, amount, status::text AS status, idempotency_key "
                        + "FROM deposit.deposit_order WHERE idempotency_key = ?",
                ORDER_MAPPER, idempotencyKey).stream().findFirst();
    }

    @Override
    public DepositOrder save(DepositOrder order) {
        jdbc.update(
                "INSERT INTO deposit.deposit_order(order_id, account_id, pack_id, currency, amount, idempotency_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                order.orderId(), order.accountId(), order.packId(), order.currency(), order.amount(),
                order.idempotencyKey());
        return order;
    }
}
