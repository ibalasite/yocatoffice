package com.sayyogames.yocat.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Async configuration for Spring Modulith {@code @ApplicationModuleListener} delivery (EDD §4.6 Outbox).
 *
 * <p>Module listeners are {@code @Async @TransactionalEventListener(AFTER_COMMIT)}. The events in this
 * system drive <em>deterministic</em> state the API (and the BDD/contract suites) observe immediately after
 * the producing call returns — e.g. the wallet is opened the instant {@code MemberRegistered} commits, and
 * credited the instant {@code DepositConfirmed} commits. To make that delivery synchronous and
 * order-deterministic (while still flowing through the transactional Outbox / event-publication registry so
 * the registry records each publication), the async executor named {@code taskExecutor} is a
 * {@link SyncTaskExecutor}: the listener runs inline on the after-commit callback thread, before the request
 * thread returns. There is no other {@code @Async} work in this service, so running it in-thread is correct.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }
}
