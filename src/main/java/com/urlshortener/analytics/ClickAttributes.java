package com.urlshortener.analytics;

import java.net.URI;
import java.util.Locale;

/**
 * Reduces raw request headers to coarse, non-identifying attributes before anything is stored.
 * Pure functions so they are trivially unit-testable.
 */
public final class ClickAttributes {

    static final int MAX_HOST_LENGTH = 255;

    private ClickAttributes() {
    }

    /** Returns the lower-cased host of the referrer, or null when absent or unparseable. */
    public static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer.trim()).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.length() > MAX_HOST_LENGTH ? host.substring(0, MAX_HOST_LENGTH) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Classifies a user agent into a small fixed set of families. Order matters: Edge and Opera
     * UAs also contain "Chrome", and Chrome UAs also contain "Safari".
     */
    public static String agentFamily(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")
                || ua.contains("curl") || ua.contains("wget") || ua.contains("python-requests")) {
            return "bot";
        }
        if (ua.contains("edg/")) {
            return "edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "opera";
        }
        if (ua.contains("firefox/")) {
            return "firefox";
        }
        if (ua.contains("chrome/") || ua.contains("crios/")) {
            return "chrome";
        }
        if (ua.contains("safari/")) {
            return "safari";
        }
        return "other";
    }
}
