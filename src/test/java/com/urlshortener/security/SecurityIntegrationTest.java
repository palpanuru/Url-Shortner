package com.urlshortener.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void managementApiRequiresApiKey() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.type").value("https://errors.urlshortener.local/unauthorized"));
    }

    @Test
    void wrongApiKeyIsRejected() throws Exception {
        mvc.perform(get("/api/v1/links/anything").header(TestKeys.HEADER, "not-a-real-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void linksAreVisibleOnlyToTheirOwner() throws Exception {
        mvc.perform(post("/api/v1/links").header(TestKeys.HEADER, TestKeys.ALPHA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/owned\",\"alias\":\"alpha-owned\"}"))
                .andExpect(status().isCreated());

        // Another client sees 404 (not 403) so it cannot probe which codes exist.
        mvc.perform(get("/api/v1/links/alpha-owned").header(TestKeys.HEADER, TestKeys.BETA))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/links/alpha-owned/stats").header(TestKeys.HEADER, TestKeys.BETA))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/links/alpha-owned").header(TestKeys.HEADER, TestKeys.BETA))
                .andExpect(status().isNotFound());

        // The redirect itself is public, and the owner still has full access.
        mvc.perform(get("/alpha-owned")).andExpect(status().isFound());
        mvc.perform(get("/api/v1/links/alpha-owned").header(TestKeys.HEADER, TestKeys.ALPHA))
                .andExpect(status().isOk());
    }

    @Test
    void operationalEndpointsStayReachable() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
