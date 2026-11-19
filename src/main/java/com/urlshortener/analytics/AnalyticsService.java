package com.urlshortener.analytics;

import com.urlshortener.analytics.dto.LinkStats;
import com.urlshortener.analytics.dto.LinkStats.Bucket;
import com.urlshortener.analytics.dto.LinkStats.DailyClicks;
import com.urlshortener.config.AnalyticsProperties;
import com.urlshortener.link.LinkNotFoundException;
import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkRepository;
import com.urlshortener.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    static final int TOP_REFERRERS = 5;

    private final ShortLinkRepository links;
    private final ClickEventRepository clicks;
    private final AnalyticsProperties properties;
    private final Clock clock;

    public AnalyticsService(ShortLinkRepository links, ClickEventRepository clicks,
            AnalyticsProperties properties, Clock clock) {
        this.links = links;
        this.clicks = clicks;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Stats for the last {@code days} UTC days including today. The daily series is zero-filled so
     * clients can chart it directly.
     */
    @Transactional(readOnly = true)
    public LinkStats stats(String code, int days) {
        if (days < 1 || days > properties.maxWindowDays()) {
            throw new InvalidWindowException(properties.maxWindowDays());
        }
        ShortLink link = links.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = today.minusDays(days - 1L);

        Map<LocalDate, Long> byDay = clicks.countByDay(link.getId(), from).stream()
                .collect(Collectors.toMap(ClickEventRepository.DayCount::day,
                        ClickEventRepository.DayCount::clicks));
        List<DailyClicks> daily = new ArrayList<>(days);
        long inWindow = 0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            long n = byDay.getOrDefault(d, 0L);
            inWindow += n;
            daily.add(new DailyClicks(d, n));
        }

        List<Bucket> referrers = clicks
                .topReferrers(link.getId(), from, PageRequest.of(0, TOP_REFERRERS)).stream()
                .map(r -> new Bucket(r.key(), r.clicks()))
                .toList();
        List<Bucket> agents = clicks.countByAgentFamily(link.getId(), from).stream()
                .map(r -> new Bucket(r.key(), r.clicks()))
                .toList();

        return new LinkStats(link.getCode(), link.getClickCount(), days, inWindow, daily,
                referrers, agents);
    }

    static class InvalidWindowException extends ApiException {
        InvalidWindowException(int max) {
            super(HttpStatus.BAD_REQUEST, "invalid-window", "days must be between 1 and " + max);
        }
    }
}
