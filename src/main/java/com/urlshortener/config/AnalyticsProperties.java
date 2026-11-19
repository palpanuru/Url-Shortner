package com.urlshortener.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(
        @Min(1) @Max(64) int threads,
        @Min(1) int queueCapacity,
        @Min(1) @Max(365) int maxWindowDays) {
}
