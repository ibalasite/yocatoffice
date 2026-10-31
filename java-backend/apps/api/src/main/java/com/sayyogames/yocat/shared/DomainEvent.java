package com.sayyogames.yocat.shared;

import java.time.Instant;

/**
 * Marker supertype for all cross-entity domain facts (EDD §4.5.2 class-application
 * {@code DomainEvent}, §4.6.1 Domain Events 完整清單). Pure domain — no Spring, no JDBC.
 *
 * <p>A domain event is an immutable record of something that already happened inside an
 * aggregate (member registered, wallet balance changed, deposit confirmed, …). It is the
 * domain layer's cross-boundary coordination contract: an owning BC publishes it and other
 * BCs react, without the owning aggregate depending on the consumer. The application layer's
 * {@code DomainEventPublisher} port transports instances of this type to the Spring Modulith
 * event registry / Outbox (EDD §4.6, §21); the domain itself stays transport-agnostic.
 *
 * <p>Every event exposes {@link #occurredAt()}, the instant the fact became true, which the
 * publisher uses for ordering and the Outbox uses for replay.
 */
public interface DomainEvent {

    /** The instant at which this domain fact occurred. */
    Instant occurredAt();
}
