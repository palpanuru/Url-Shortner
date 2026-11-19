package com.urlshortener.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Link lifecycle and safety rules.
 *
 * @param defaultTtl how long a link lives when the caller does not choose an expiry
 * @param maxTtl the latest expiry a caller may request
 * @param purgeAfter how long an expired link is kept (still answering 410) before deletion
 * @param blockPrivateAddresses reject loopback/private/link-local IP literals and localhost
 * @param blockedDomains domains (and their subdomains) that may never be shortened
 */
@Validated
@ConfigurationProperties(prefix = "app.links")
public record LinkProperties(
        @NotNull Duration defaultTtl,
        @NotNull Duration maxTtl,
        @NotNull Duration purgeAfter,
        boolean blockPrivateAddresses,
        List<String> blockedDomains) {

    public LinkProperties {
        blockedDomains = blockedDomains == null ? List.of() : List.copyOf(blockedDomains);
        if (defaultTtl != null && maxTtl != null && defaultTtl.compareTo(maxTtl) > 0) {
            throw new IllegalArgumentException("app.links.default-ttl must not exceed max-ttl");
        }
    }
}
