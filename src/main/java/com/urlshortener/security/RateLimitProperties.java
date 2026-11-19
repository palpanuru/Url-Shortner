package com.urlshortener.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @NotNull @Valid Limit create,
        @NotNull @Valid Limit redirect) {

    /**
     * Token bucket: {@code capacity} is the burst size, {@code perMinute} the sustained rate.
     */
    public record Limit(@Min(1) int capacity, @Min(1) int perMinute) {
    }
}
