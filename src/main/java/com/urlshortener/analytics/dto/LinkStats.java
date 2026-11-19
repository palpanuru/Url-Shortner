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

    public record DailyClicks(LocalDate date, long clicks) {
    }

    public record Bucket(String key, long clicks) {
    }
}
