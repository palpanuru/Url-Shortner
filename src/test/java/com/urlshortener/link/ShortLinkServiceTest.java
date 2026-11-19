package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.config.AppProperties;
import com.urlshortener.link.dto.CreateLinkRequest;
import com.urlshortener.link.dto.LinkResponse;
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
        AppProperties props = new AppProperties("http://sho.rt/", 7, 3);
        service = new ShortLinkService(repository, codeGenerator, new UrlPolicy(),
                new AliasPolicy(), props, Clock.fixed(NOW, ZoneOffset.UTC), event -> { });
    }

    @Test
    void retriesOnCodeCollisionAndSucceeds() {
        when(codeGenerator.generate(7)).thenReturn("AAAAAAA", "BBBBBBB");
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        LinkResponse response = service.create(new CreateLinkRequest("https://example.com", null));

        assertThat(response.code()).isEqualTo("BBBBBBB");
        assertThat(response.shortUrl()).isEqualTo("http://sho.rt/BBBBBBB");
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void givesUpAfterConfiguredAttempts() {
        when(codeGenerator.generate(7)).thenReturn("AAAAAAA");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null)))
                .isInstanceOf(CodeGenerationException.class);
        verify(repository, times(3)).saveAndFlush(any());
    }

    @Test
    void aliasCollisionIsReportedAsConflictWithoutRetry() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", "promo")))
                .isInstanceOf(AliasConflictException.class);
        verify(repository, times(1)).saveAndFlush(any());
    }
}
