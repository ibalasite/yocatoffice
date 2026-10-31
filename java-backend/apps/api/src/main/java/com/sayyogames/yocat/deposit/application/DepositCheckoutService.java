package com.sayyogames.yocat.deposit.application;

import com.sayyogames.yocat.deposit.domain.DepositConfirmed;
import com.sayyogames.yocat.deposit.domain.DepositOrder;
import com.sayyogames.yocat.deposit.domain.DepositOrderRepository;
import com.sayyogames.yocat.deposit.domain.DepositPack;
import com.sayyogames.yocat.deposit.domain.DepositPackRepository;
import com.sayyogames.yocat.platform.web.IdempotencyConflictException;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; deposit BC checkout (EDD §4.5.4 / API §3 deposit {@code POST /deposit/checkout}).
 *
 * <p>Phase A fake-pay: an order is created {@code confirmed} and {@code DepositConfirmed} is published in the
 * same transaction (Outbox) so the wallet BC credits the paid currency exactly once. Idempotency is keyed on
 * the client {@code Idempotency-Key} (SCHEMA UNIQUE): the same key with the same pack replays the original
 * order and publishes <em>no</em> new event (credited once); the same key with a different pack is a 409
 * conflict and creates nothing (API §7).</p>
 */
@Service
public class DepositCheckoutService {

    private final DepositPackRepository packRepo;
    private final DepositOrderRepository orderRepo;
    private final DomainEventPublisher publisher;

    public DepositCheckoutService(DepositPackRepository packRepo, DepositOrderRepository orderRepo,
                                  DomainEventPublisher publisher) {
        this.packRepo = packRepo;
        this.orderRepo = orderRepo;
        this.publisher = publisher;
    }

    @Transactional
    public DepositOrder checkout(long accountId, String packId, String idempotencyKey) {
        DepositPack pack = packRepo.findByPackId(packId)
                .orElseThrow(() -> new IllegalArgumentException("儲值包不正確"));

        Optional<DepositOrder> existing = orderRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            DepositOrder prior = existing.get();
            if (!prior.packId().equals(packId)) {
                throw new IdempotencyConflictException("冪等鍵衝突");
            }
            return prior; // replay — no new order, no new event (wallet already credited once)
        }

        long numericId = Math.abs(ThreadLocalRandom.current().nextLong());
        DepositOrder order = new DepositOrder(
                "ord_" + numericId, accountId, pack.packId(), pack.currency(), pack.amount(),
                "confirmed", idempotencyKey);
        orderRepo.save(order);

        // currency travels as its wire token (gems/cash); the wallet consumer maps it to its own Currency,
        // so deposit does not depend on the wallet module (HC-5 acyclic DAG).
        publisher.publish(new DepositConfirmed(AccountId.of(accountId), numericId, pack.currency(), pack.amount()));
        return order;
    }
}
