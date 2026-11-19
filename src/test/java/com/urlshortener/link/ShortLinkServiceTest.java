package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.config.AppProperties;
import com.urlshortener.config.LinkProperties;
import com.urlshortener.link.dto.CreateLinkRequest;
import com.urlshortener.link.dto.LinkResponse;
import com.urlshortener.support.TestFixtures;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit tests for the creation path's retry and conflict handling (no Spring context). */
class ShortLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private ShortLinkRepository repository;
    private CodeGenerator codeGenerator;
    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShortLinkRepository.class);
        codeGenerator = mock(CodeGenerator.class);
        AppProperties props = TestFixtures.appProperties();
        LinkProperties links = TestFixtures.linkProperties();
        service = new ShortLinkService(repository, codeGenerator, new UrlPolicy(props, links),
                new AliasPolicy(), props, links, new LinkCache(), Clock.fixed(NOW, ZoneOffset.UTC),
                event -> { });
    }

    @Test
    void retriesOnCodeCollisionAndSucceeds() {
        when(codeGenerator.generate(7)).thenReturn("AAAAAAA", "BBBBBBB");
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        LinkResponse response = service.create(new CreateLinkRequest("https://example.com", null), "alpha");

        assertThat(response.code()).isEqualTo("BBBBBBB");
        assertThat(response.shortUrl()).isEqualTo("http://sho.rt/BBBBBBB");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(365)));
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void givesUpAfterConfiguredAttempts() {
        when(codeGenerator.generate(7)).thenReturn("AAAAAAA");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null), "alpha"))
                .isInstanceOf(CodeGenerationException.class);
        verify(repository, times(3)).saveAndFlush(any());
    }

    @Test
    void aliasCollisionIsReportedAsConflictWithoutRetry() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", "promo"), "alpha"))
                .isInstanceOf(AliasConflictException.class);
        verify(repository, times(1)).saveAndFlush(any());
    }

    @Test
    void honoursRequestedExpiryWithinBounds() {
        when(codeGenerator.generate(7)).thenReturn("CCCCCCC");
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        Instant requested = NOW.plus(Duration.ofDays(10));

        LinkResponse response = service.create(
                new CreateLinkRequest("https://example.com", null, requested), "alpha");

        assertThat(response.expiresAt()).isEqualTo(requested);
    }

    @Test
    void rejectsExpiryInThePastOrBeyondMaximum() {
        assertThatThrownBy(() -> service.create(
                new CreateLinkRequest("https://example.com", null, NOW), "alpha"))
                .isInstanceOf(InvalidExpiryException.class);
        assertThatThrownBy(() -> service.create(
                new CreateLinkRequest("https://example.com", null, NOW.plus(Duration.ofDays(731))),
                "alpha"))
                .isInstanceOf(InvalidExpiryException.class)
                .hasMessageContaining("730 days");
    }
}
