package com.sayyogames.yocat.shared;

/**
 * &lt;&lt;Port&gt;&gt; for publishing {@link DomainEvent}s out of the Application layer (EDD §4.5.2 class-application).
 *
 * <p>Domain/application-owned outbound port; ModulithEventPublisher (infrastructure, transactional Outbox)
 * implements it via Spring DI (DIP, EDD §4.1). Use cases depend on this interface, never on the adapter.</p>
 */
public interface DomainEventPublisher {

    /** Publish a domain event in the current transaction (Outbox pattern). Caller: Spend/TopUp/RegisterMember use cases (EDD §4.5.4). */
    void publish(DomainEvent event);
}
