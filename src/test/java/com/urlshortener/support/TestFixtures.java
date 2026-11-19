package com.urlshortener.support;

import com.urlshortener.config.AppProperties;
import com.urlshortener.config.LinkProperties;
import java.time.Duration;
import java.util.List;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static AppProperties appProperties() {
        return new AppProperties("http://sho.rt/", 7, 3);
    }

    public static LinkProperties linkProperties(String... blockedDomains) {
        return new LinkProperties(Duration.ofDays(365), Duration.ofDays(730), Duration.ofDays(30),
                true, List.of(blockedDomains));
    }
}
