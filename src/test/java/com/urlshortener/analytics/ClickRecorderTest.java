package com.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.link.ShortLinkRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClickRecorderTest {

    private final ClickEventRepository clicks = mock(ClickEventRepository.class);
    private final ShortLinkRepository links = mock(ShortLinkRepository.class);
    private final ClickRecorder recorder = new ClickRecorder(clicks, links);

    @Test
    void storesOnlyCoarseAttributes() {
        when(links.incrementClickCount(42L)).thenReturn(1);
        Instant at = Instant.parse("2026-03-01T23:59:59Z");

        recorder.onLinkClicked(new LinkClickedEvent(42L, at,
                "https://social.example/post/123?session=abc", "curl/8.0"));

        ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clicks).save(saved.capture());
        assertThat(saved.getValue().getLinkId()).isEqualTo(42L);
        assertThat(saved.getValue().getClickedOn()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(saved.getValue().getReferrerHost()).isEqualTo("social.example");
        assertThat(saved.getValue().getAgentFamily()).isEqualTo("bot");
    }

    @Test
    void skipsEventWhenLinkWasDeletedMeanwhile() {
        when(links.incrementClickCount(7L)).thenReturn(0);

        recorder.onLinkClicked(new LinkClickedEvent(7L, Instant.now(), null, null));

        verify(clicks, never()).save(any());
    }
}
