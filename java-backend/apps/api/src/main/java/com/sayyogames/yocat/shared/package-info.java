/**
 * Shared kernel: cross-BC value objects ({@code AccountId}, {@code Money}), the {@code DomainEvent} contract +
 * {@code DomainEventPublisher} port, and cross-BC domain-event contracts ({@code MemberRegistered}) consumed by
 * more than one bounded context (EDD §2 / §4.6). Declared an OPEN Spring Modulith module so every BC may depend
 * on the kernel; the kernel itself depends on no BC (pure sink), so it never participates in a cycle (HC-5).
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.sayyogames.yocat.shared;
