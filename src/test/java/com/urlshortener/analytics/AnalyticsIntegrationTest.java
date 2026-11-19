package com.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /**
     * Regression test for the brownfield bug: v1 incremented click_count with a
     * load-modify-save inside the redirect transaction, so concurrent redirects lost updates.
     */
    @Test
    void concurrentRedirectsAreAllCounted() throws Exception {
        String code = create("https://example.com/concurrency");
        int clicks = 200;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < clicks; i++) {
                tasks.add(() -> mvc.perform(get("/" + code)).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                assertThat(f.get()).isEqualTo(302);
            }
        } finally {
            pool.shutdown();
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(clickCount(code)).isEqualTo(clicks));
    }

    @Test
    void statsBreakDownClicksByReferrerAndAgent() throws Exception {
        String code = create("https://example.com/stats");
        click(code, "https://news.example.com/story", "Mozilla/5.0 Firefox/121.0");
        click(code, "https://news.example.com/other", "Mozilla/5.0 Chrome/120.0 Safari/537.36");
        click(code, "https://blog.example.org/", "Mozilla/5.0 Chrome/120.0 Safari/537.36");
        click(code, null, null);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/v1/links/" + code + "/stats?days=3"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalClicks").value(4))
                        .andExpect(jsonPath("$.clicksInWindow").value(4))
                        .andExpect(jsonPath("$.windowDays").value(3))
                        .andExpect(jsonPath("$.daily.length()").value(3))
                        .andExpect(jsonPath("$.daily[2].clicks").value(4))
                        .andExpect(jsonPath("$.daily[0].clicks").value(0))
                        .andExpect(jsonPath("$.topReferrers[0].key").value("news.example.com"))
                        .andExpect(jsonPath("$.topReferrers[0].clicks").value(2))
                        .andExpect(jsonPath("$.agents[0].key").value("chrome"))
                        .andExpect(jsonPath("$.agents[0].clicks").value(2)));
    }

    @Test
    void statsWindowIsBounded() throws Exception {
        String code = create("https://example.com/window");
        mvc.perform(get("/api/v1/links/" + code + "/stats?days=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/links/" + code + "/stats?days=91")).andExpect(status().isBadRequest());
    }

    @Test
    void statsForUnknownLinkIs404() throws Exception {
        mvc.perform(get("/api/v1/links/missing1/stats")).andExpect(status().isNotFound());
    }

    private void click(String code, String referrer, String userAgent) throws Exception {
        var req = get("/" + code);
        if (referrer != null) {
            req.header(HttpHeaders.REFERER, referrer);
        }
        if (userAgent != null) {
            req.header(HttpHeaders.USER_AGENT, userAgent);
        }
        mvc.perform(req).andExpect(status().isFound());
    }

    private long clickCount(String code) throws Exception {
        String body = mvc.perform(get("/api/v1/links/" + code))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("clickCount").asLong();
    }

    private String create(String url) throws Exception {
        String body = mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("code").asText();
    }
}
