package com.urlshortener.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
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
 *
 * <p>The meter registry is looked up lazily: AsyncConfigurer beans are created very early, and
 * eagerly injecting the registry here would initialise it before its customisers run.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    static final String DROPPED = "analytics.events.dropped";
    static final String FAILED = "analytics.events.failed";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final ObjectProvider<MeterRegistry> meterRegistry;

    public AsyncConfig(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean(name = "analyticsExecutor")
    public ThreadPoolTaskExecutor analyticsExecutor(AnalyticsProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analytics-");
        executor.setCorePoolSize(props.threads());
        executor.setMaxPoolSize(props.threads());
        executor.setQueueCapacity(props.queueCapacity());
        // Count only; no per-event log line. Logging every drop turned overload into a log flood
        // in the load test (8.8k WARN lines in ~7s) - alert on the metric instead.
        executor.setRejectedExecutionHandler((task, pool) -> increment(DROPPED));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            increment(FAILED);
            log.warn("Async task {} failed", method.getName(), ex);
        };
    }

    /** Register counters at startup so dashboards and alerts see an explicit zero. */
    @EventListener(ApplicationReadyEvent.class)
    public void registerMeters() {
        meterRegistry.ifAvailable(registry -> {
            Counter.builder(DROPPED)
                    .description("Click events discarded because the analytics queue was full")
                    .register(registry);
            Counter.builder(FAILED)
                    .description("Click events that failed to persist")
                    .register(registry);
        });
    }

    private void increment(String name) {
        meterRegistry.ifAvailable(registry -> Counter.builder(name).register(registry).increment());
    }
}
