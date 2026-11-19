package com.urlshortener.link;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * In-process read-through cache for the redirect hot path.
 *
 * <p>Only positive lookups are cached: caching misses would let a 404 outlive the creation of a
 * new alias. Entries carry the expiry so an expired link is never served from cache. Deletes
 * invalidate locally; with several instances, another node may serve a deleted link for at most
 * {@link #TTL} (documented trade-off; a shared cache or pub/sub invalidation removes it).
 */
@Component
public class LinkCache {

    static final Duration TTL = Duration.ofSeconds(60);

    public record CachedLink(long id, String targetUrl, Instant expiresAt) {
    }

    private final Cache<String, CachedLink> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(TTL)
            .recordStats()
            .build();

    public Optional<CachedLink> get(String code, Function<String, Optional<CachedLink>> loader) {
        CachedLink hit = cache.getIfPresent(code);
        if (hit != null) {
            return Optional.of(hit);
        }
        Optional<CachedLink> loaded = loader.apply(code);
        loaded.ifPresent(link -> cache.put(code, link));
        return loaded;
    }

    public void invalidate(String code) {
        cache.invalidate(code);
    }

    long hitCount() {
        return cache.stats().hitCount();
    }
}
