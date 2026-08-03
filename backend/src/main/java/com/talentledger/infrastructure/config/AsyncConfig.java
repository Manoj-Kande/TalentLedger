package com.talentledger.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * File parser executor — uses virtual threads (Java 21).
     * Each file parse runs on its own virtual thread.
     */
    @Bean("parserExecutor")
    public Executor parserExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("file-parser-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /**
     * Outbox poller executor — uses virtual threads.
     * Polls outbox_events every 5 seconds.
     */
    @Bean("outboxExecutor")
    public Executor outboxExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("outbox-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /**
     * Async task executor for general @Async methods.
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();
        return executor;
    }
}
