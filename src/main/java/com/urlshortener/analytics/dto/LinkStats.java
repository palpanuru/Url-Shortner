package com.urlshortener.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record LinkStats(
        String code,
        long totalClicks,
        int windowDays,
        long clicksInWindow,
        List<DailyClicks> daily,
        List<Bucket> topReferrers,
        List<Bucket> agents) {

    /** Defensive, immutable copies: a response object should never alias service state. */
    public LinkStats {
        daily = List.copyOf(daily);
        topReferrers = List.copyOf(topReferrers);
        agents = List.copyOf(agents);
    }

    public record DailyClicks(LocalDate date, long clicks) {
    }

    public record Bucket(String key, long clicks) {
    }
}
