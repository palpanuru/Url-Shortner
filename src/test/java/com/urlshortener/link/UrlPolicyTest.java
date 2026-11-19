package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlPolicyTest {

    private final UrlPolicy policy = new UrlPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com",
        "http://example.com/path?q=1#frag",
        "HTTPS://Example.com/Upper"
    })
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(policy.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(policy.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlank(String url) {
        assertThatThrownBy(() -> policy.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "data:text/html;base64,PHNjcmlwdD4=",
        "ftp://example.com/file",
        "file:///etc/passwd",
        "example.com/no-scheme",
        "https:///no-host",
        "https://exa mple.com"
    })
    void rejectsUnsafeOrMalformedUrls(String url) {
        assertThatThrownBy(() -> policy.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsOverlongUrls() {
        String url = "https://example.com/" + "a".repeat(UrlPolicy.MAX_LENGTH);
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("exceeds");
    }
}
