package com.urlshortener.security;

import java.util.function.LongSupplier;

/**
 * Classic token bucket. Thread-safe via a per-bucket monitor: contention is per client, so a
 * plain lock is cheaper and clearer than a CAS loop here.
 */
final class TokenBucket {

    private static final long NANOS_PER_MINUTE = 60_000_000_000L;

    private final long capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;
    private double tokens;
    private long lastRefill;

    TokenBucket(long capacity, long perMinute, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.tokensPerNano = (double) perMinute / NANOS_PER_MINUTE;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefill = nanoClock.getAsLong();
    }

    /** Takes one token; returns 0 if allowed, otherwise the seconds until a token is available. */
    synchronized long tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        double nanosUntilToken = (1 - tokens) / tokensPerNano;
        return Math.max(1, (long) Math.ceil(nanosUntilToken / 1_000_000_000d));
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefill;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
            lastRefill = now;
        }
    }
}
