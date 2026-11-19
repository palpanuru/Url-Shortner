package com.urlshortener.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.support.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "app.rate-limit.create.capacity=2",
    "app.rate-limit.create.per-minute=1",
    "app.rate-limit.redirect.capacity=3",
    "app.rate-limit.redirect.per-minute=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void creationIsLimitedPerClient() throws Exception {
        mvc.perform(create(TestKeys.ALPHA)).andExpect(status().isCreated());
        mvc.perform(create(TestKeys.ALPHA)).andExpect(status().isCreated());
        mvc.perform(create(TestKeys.ALPHA))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://errors.urlshortener.local/rate-limited"));

        // A different client has its own budget.
        mvc.perform(create(TestKeys.BETA)).andExpect(status().isCreated());
    }

    @Test
    void redirectsAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/abc123").with(r -> withIp(r, "203.0.113.7")))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/abc123").with(r -> withIp(r, "203.0.113.7")))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/abc123").with(r -> withIp(r, "203.0.113.8")))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.mock.web.MockHttpServletRequest withIp(
            org.springframework.mock.web.MockHttpServletRequest request, String ip) {
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletRequestBuilder create(String key) {
        return post("/api/v1/links").header(TestKeys.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/rl\"}");
    }
}
