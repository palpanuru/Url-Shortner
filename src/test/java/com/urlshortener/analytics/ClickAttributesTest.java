package com.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClickAttributesTest {

    @Test
    void referrerIsReducedToLowerCaseHost() {
        assertThat(ClickAttributes.referrerHost("https://News.Example.com/a/b?token=secret"))
                .isEqualTo("news.example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not a url", "/relative/path", "::::"})
    void unusableReferrersBecomeNull(String referrer) {
        assertThat(ClickAttributes.referrerHost(referrer)).isNull();
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Edg/120.0|edge",
        "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 OPR/105.0|opera",
        "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0|firefox",
        "Mozilla/5.0 (Macintosh) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36|chrome",
        "Mozilla/5.0 (iPhone) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1|safari",
        "Googlebot/2.1 (+http://www.google.com/bot.html)|bot",
        "curl/8.4.0|bot",
        "SomethingElse/1.0|other"
    })
    void classifiesAgentFamilies(String userAgent, String expected) {
        assertThat(ClickAttributes.agentFamily(userAgent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingAgentIsUnknown(String userAgent) {
        assertThat(ClickAttributes.agentFamily(userAgent)).isEqualTo("unknown");
    }
}
