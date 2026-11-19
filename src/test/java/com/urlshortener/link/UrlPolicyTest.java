package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlPolicyTest {

    private final UrlPolicy policy =
            new UrlPolicy(TestFixtures.appProperties(), TestFixtures.linkProperties("evil.example"));

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

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost:8080/admin",
        "http://app.localhost/",
        "http://printer.local/",
        "http://metadata.internal/",
        "http://127.0.0.1/",
        "http://10.1.2.3/",
        "http://172.16.0.1/",
        "http://192.168.1.1/",
        "http://169.254.169.254/latest/meta-data",
        "http://100.64.0.1/",
        "http://0.0.0.0/",
        "http://[::1]/",
        "http://[fd00::1]/",
        "http://[fe80::1]/"
    })
    void rejectsLocalAndPrivateAddresses(String url) {
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://8.8.8.8/", "http://[2001:4860:4860::8888]/", "http://172.32.0.1/"})
    void allowsPublicIpLiterals(String url) {
        assertThat(policy.validate(url)).isEqualTo(url);
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertThatThrownBy(() -> policy.validate("https://bank.example@evil.example/login"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("credentials");
    }

    @Test
    void rejectsLinksBackToThisShortener() {
        assertThatThrownBy(() -> policy.validate("http://SHO.RT/abc1234"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("shortener");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/", "https://login.evil.example/x"})
    void rejectsBlockedDomainsAndSubdomains(String url) {
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void blockedDomainMatchIsNotASuffixTrick() {
        assertThat(policy.validate("https://notevil.example/")).isEqualTo("https://notevil.example/");
    }
}
