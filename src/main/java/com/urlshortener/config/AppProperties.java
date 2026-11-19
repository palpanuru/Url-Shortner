package com.urlshortener.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed application settings. Validated at startup so a bad config fails fast
 * instead of surfacing as a runtime error on the first request.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String baseUrl,
        @Min(6) @Max(12) int codeLength,
        @Min(1) @Max(10) int maxCodeGenerationAttempts) {
}
