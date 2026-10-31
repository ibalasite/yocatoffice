package com.sayyogames.yocat.platform;

import com.sayyogames.yocat.shared.DomainEvent;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * &lt;&lt;Adapter&gt;&gt; Infrastructure implementation of the application-owned {@link DomainEventPublisher}
 * port (EDD §4.5.2 class-application {@code ModulithEventPublisher}; §4.6 Transactional Outbox).
 *
 * <p>Delegates to Spring's {@link ApplicationEventPublisher}. With
 * {@code spring-modulith-events-jdbc} active ({@code spring.modulith.events.jdbc.enabled=true},
 * application.yml), each event published inside the caller's {@code @Transactional} boundary is written
 * to {@code modulith.event_publication} in the <em>same</em> database transaction (Outbox / Event
 * Publication Registry, SCHEMA §3.8, migration V009), then delivered to local
 * {@code @ApplicationModuleListener}s after commit — giving at-least-once, module-decoupled delivery
 * without an external broker (ADR-003 / EDD §3.3). Use cases depend on the
 * {@link DomainEventPublisher} abstraction, never on this adapter (DIP, EDD §4.1).
 */
@Component
public class ModulithEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher events;

    public ModulithEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void publish(DomainEvent event) {
        events.publishEvent(event);
    }
}
