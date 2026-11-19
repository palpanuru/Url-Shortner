package com.urlshortener.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-instance rate limiter. Buckets live in a bounded, expiring cache so a flood of
 * distinct client keys cannot exhaust memory.
 *
 * <p>Trade-off: limits are per instance (N instances allow roughly N times the rate). Acceptable
 * for abuse damping in a prototype; a shared store (Redis + Bucket4j) or the API gateway is the
 * production answer.
 */
@Component
public class RateLimiter {

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private final LongSupplier nanoClock;

    @Autowired
    public RateLimiter() {
        this(System::nanoTime);
    }

    RateLimiter(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /** Returns 0 when allowed, otherwise the Retry-After in seconds. */
    public long tryAcquire(String key, RateLimitProperties.Limit limit) {
        return buckets.get(key, k -> new TokenBucket(limit.capacity(), limit.perMinute(), nanoClock))
                .tryConsume();
    }
}
