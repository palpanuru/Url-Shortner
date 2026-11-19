package com.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private final AtomicLong nanos = new AtomicLong();

    @Test
    void allowsBurstUpToCapacityThenRejectsWithRetryAfter() {
        TokenBucket bucket = new TokenBucket(3, 60, nanos::get); // 1 token / second

        assertThat(bucket.tryConsume()).isZero();
        assertThat(bucket.tryConsume()).isZero();
        assertThat(bucket.tryConsume()).isZero();
        assertThat(bucket.tryConsume()).isEqualTo(1);
    }

    @Test
    void refillsOverTimeButNeverBeyondCapacity() {
        TokenBucket bucket = new TokenBucket(2, 60, nanos::get);
        bucket.tryConsume();
        bucket.tryConsume();

        nanos.addAndGet(1_000_000_000L); // +1s -> 1 token
        assertThat(bucket.tryConsume()).isZero();
        assertThat(bucket.tryConsume()).isPositive();

        nanos.addAndGet(3_600_000_000_000L); // +1h -> capped at 2
        assertThat(bucket.tryConsume()).isZero();
        assertThat(bucket.tryConsume()).isZero();
        assertThat(bucket.tryConsume()).isPositive();
    }

    @Test
    void retryAfterReflectsSlowRates() {
        TokenBucket bucket = new TokenBucket(1, 1, nanos::get); // 1 token / minute
        bucket.tryConsume();
        assertThat(bucket.tryConsume()).isEqualTo(60);
    }

    @Test
    void limiterKeepsIndependentBucketsPerKey() {
        RateLimiter limiter = new RateLimiter(nanos::get);
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, 1);

        assertThat(limiter.tryAcquire("a", limit)).isZero();
        assertThat(limiter.tryAcquire("a", limit)).isPositive();
        assertThat(limiter.tryAcquire("b", limit)).isZero();
    }
}
