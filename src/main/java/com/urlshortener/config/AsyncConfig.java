package com.urlshortener.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor for analytics writes.
 *
 * <p>Trade-off: under sustained overload the queue fills and new click events are DROPPED
 * (counted in {@code analytics.events.dropped}) rather than blocking redirect threads. For a URL
 * shortener, redirect latency/availability matters more than perfect analytics. A durable queue
 * (Kafka/SQS) is the production answer; see docs/ENGINEERING_SUMMARY.md.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final Counter dropped;
    private final Counter failed;

    public AsyncConfig(MeterRegistry meterRegistry) {
        this.dropped = Counter.builder("analytics.events.dropped")
                .description("Click events discarded because the analytics queue was full")
                .register(meterRegistry);
        this.failed = Counter.builder("analytics.events.failed")
                .description("Click events that failed to persist")
                .register(meterRegistry);
    }

    @Bean(name = "analyticsExecutor")
    public ThreadPoolTaskExecutor analyticsExecutor(AnalyticsProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analytics-");
        executor.setCorePoolSize(props.threads());
        executor.setMaxPoolSize(props.threads());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setRejectedExecutionHandler((task, pool) -> {
            dropped.increment();
            log.warn("Analytics queue full; dropping click event");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        // Only the named analytics executor is used; the default is never selected.
        return null;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            failed.increment();
            log.warn("Async task {} failed", method.getName(), ex);
        };
    }
}
