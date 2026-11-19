package com.urlshortener.analytics;

import com.urlshortener.link.ShortLinkRepository;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists click analytics off the redirect's request thread. A failure here can never affect
 * the redirect the user already received: exceptions surface in the async uncaught-exception
 * handler (see {@code AsyncConfig}), which logs them and increments a failure metric.
 */
@Component
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private final ClickEventRepository clicks;
    private final ShortLinkRepository links;

    public ClickRecorder(ClickEventRepository clicks, ShortLinkRepository links) {
        this.clicks = clicks;
        this.links = links;
    }

    @Async("analyticsExecutor")
    @EventListener
    @Transactional
    public void onLinkClicked(LinkClickedEvent event) {
        // Atomic SQL increment: no read-modify-write, so concurrent clicks cannot be lost.
        int updated = links.incrementClickCount(event.linkId());
        if (updated == 0) {
            log.debug("Link {} deleted before its click was recorded", event.linkId());
            return;
        }
        clicks.save(new ClickEvent(
                event.linkId(),
                event.occurredAt(),
                event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(),
                ClickAttributes.referrerHost(event.referrer()),
                ClickAttributes.agentFamily(event.userAgent())));
    }
}
