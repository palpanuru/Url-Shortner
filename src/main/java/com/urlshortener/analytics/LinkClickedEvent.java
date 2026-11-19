package com.urlshortener.analytics;

import java.time.Instant;

/** Published on every successful redirect; consumed asynchronously by {@link ClickRecorder}. */
public record LinkClickedEvent(long linkId, Instant occurredAt, String referrer, String userAgent) {
}
