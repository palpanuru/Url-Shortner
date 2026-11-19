package com.urlshortener.link.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateLinkRequest(
        @Schema(example = "https://www.example.com/some/long/path?with=query")
        @NotBlank @Size(max = 2048) String url,
        @Schema(description = "Optional custom alias (4-32 chars: letters, digits, '-', '_')",
                example = "spring-sale")
        @Size(max = 32) String alias,
        @Schema(description = "Optional expiry (ISO-8601). Defaults to the configured TTL and "
                + "may not exceed the configured maximum.", example = "2027-01-31T00:00:00Z")
        Instant expiresAt) {

    /** Convenience for callers that accept the default expiry. */
    public CreateLinkRequest(String url, String alias) {
        this(url, alias, null);
    }
}
