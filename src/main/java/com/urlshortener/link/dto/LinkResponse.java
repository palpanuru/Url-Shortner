package com.urlshortener.link.dto;

import java.time.Instant;

public record LinkResponse(
        String code,
        String shortUrl,
        String targetUrl,
        Instant createdAt,
        Instant expiresAt,
        long clickCount) {
}
