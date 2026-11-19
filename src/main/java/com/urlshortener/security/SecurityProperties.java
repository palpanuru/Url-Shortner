package com.urlshortener.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * API clients allowed to manage links. Only SHA-256 hashes of keys are configured, so a leaked
 * config file or heap dump does not leak usable credentials.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(@Valid List<ApiClient> apiKeys) {

    public SecurityProperties {
        apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
    }

    public record ApiClient(
            @NotBlank String clientId,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$",
                    message = "must be a lower-case hex SHA-256 digest") String sha256) {
    }
}
