package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.support.MutableClock;
import com.urlshortener.support.TestKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpiryIntegrationTest {

    static final Instant START = Instant.parse("2026-06-01T12:00:00Z");

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    ShortLinkService service;

    @Test
    void expiredLinksAnswer410ThenArePurgedAfterRetention() throws Exception {
        MutableClock testClock = (MutableClock) clock;
        mvc.perform(post("/api/v1/links").header(TestKeys.HEADER, TestKeys.ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/promo\",\"alias\":\"flash-sale\","
                                + "\"expiresAt\":\"2026-06-01T13:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2026-06-01T13:00:00Z"));

        mvc.perform(get("/flash-sale")).andExpect(status().isFound()); // also warms the cache

        testClock.advance(Duration.ofHours(2));
        mvc.perform(get("/flash-sale"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("https://errors.urlshortener.local/link-expired"));

        assertThat(service.purgeExpired()).isZero(); // still inside the 30-day retention window

        testClock.advance(Duration.ofDays(31));
        assertThat(service.purgeExpired()).isEqualTo(1);
        mvc.perform(get("/api/v1/links/flash-sale").header(TestKeys.HEADER, TestKeys.ALPHA))
                .andExpect(status().isNotFound());
    }

    @Test
    void defaultExpiryIsApplied() throws Exception {
        mvc.perform(post("/api/v1/links").header(TestKeys.HEADER, TestKeys.ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/default\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void pastExpiryIsRejected() throws Exception {
        mvc.perform(post("/api/v1/links").header(TestKeys.HEADER, TestKeys.ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://errors.urlshortener.local/invalid-expiry"));
    }
}
