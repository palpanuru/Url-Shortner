package com.urlshortener.link;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.urlshortener.support.TestKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end through HTTP, Spring MVC, JPA, Flyway and H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void createThenRedirectThenInspectThenDelete() throws Exception {
        String code = createLink("{\"url\":\"https://example.com/landing\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/links/[0-9A-Za-z]{7}")))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/landing"))
                .andExpect(jsonPath("$.shortUrl", matchesPattern("http://localhost:8080/[0-9A-Za-z]{7}")))
                .andReturn().getResponse().getContentAsString()
                .transform(this::codeOf);

        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));

        mvc.perform(get("/api/v1/links/" + code).header(TestKeys.HEADER, TestKeys.ALPHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));

        mvc.perform(delete("/api/v1/links/" + code).header(TestKeys.HEADER, TestKeys.ALPHA)).andExpect(status().isNoContent());
        mvc.perform(get("/" + code)).andExpect(status().isNotFound());
    }

    @Test
    void customAliasIsHonouredAndDuplicatesConflict() throws Exception {
        String alias = "my-alias-" + System.nanoTime(); // unique: tests may run against a persistent DB
        createLink("{\"url\":\"https://example.com\",\"alias\":\"" + alias + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(alias));

        createLink("{\"url\":\"https://example.org\",\"alias\":\"" + alias + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://errors.urlshortener.local/alias-taken"));
    }

    @Test
    void invalidUrlReturnsProblemDetail() throws Exception {
        createLink("{\"url\":\"javascript:alert(1)\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://errors.urlshortener.local/invalid-url"));
    }

    @Test
    void missingUrlFailsBeanValidation() throws Exception {
        createLink("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(matchesPattern("url: .*")));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        createLink("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void unknownCodeIs404() throws Exception {
        mvc.perform(get("/api/v1/links/doesNotExist").header(TestKeys.HEADER, TestKeys.ALPHA)).andExpect(status().isNotFound());
        mvc.perform(get("/nope123")).andExpect(status().isNotFound());
    }

    private ResultActions createLink(String body) throws Exception {
        return mvc.perform(post("/api/v1/links").header(TestKeys.HEADER, TestKeys.ALPHA).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String codeOf(String responseBody) {
        try {
            JsonNode node = json.readTree(responseBody);
            return node.get("code").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
